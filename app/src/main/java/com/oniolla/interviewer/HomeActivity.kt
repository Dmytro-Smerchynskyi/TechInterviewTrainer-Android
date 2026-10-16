package com.oniolla.interviewer

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.oniolla.interviewer.data.AppPreferences
import com.oniolla.interviewer.ui.UiEvent
import com.oniolla.interviewer.ui.GoogleTokenResult
import com.oniolla.interviewer.ui.rememberGoogleTokenRequest
import com.oniolla.interviewer.ui.home.*
import com.oniolla.interviewer.ui.theme.InterviewTrainerTheme
import com.oniolla.interviewer.ui.theme.StatusBarTheme
import com.oniolla.interviewer.ui.theme.applyStatusBarTheme
import com.oniolla.interviewer.ui.theme.AppTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class HomeActivity : ComponentActivity() {
    @Inject lateinit var preferences: AppPreferences
    private val viewModel: HomeViewModel by viewModels()
    private val profileViewModel: ProfileViewModel by viewModels()
    private val accountViewModel: AccountViewModel by viewModels()
    private val practiceViewModel: PracticeViewModel by viewModels()
    private val interviewViewModel: InterviewViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyStatusBarTheme(preferences.theme.value)
        setContent {
            val theme by preferences.theme.collectAsStateWithLifecycle()
            StatusBarTheme(this@HomeActivity, theme)
            InterviewTrainerTheme(themeMode = theme) {
                val state by viewModel.stateFlow.collectAsStateWithLifecycle()
                val profileState by profileViewModel.state.collectAsStateWithLifecycle()
                val accountState by accountViewModel.state.collectAsStateWithLifecycle()
                val requestGoogleToken = rememberGoogleTokenRequest()
                LaunchedEffect(accountState.googleRequestId) {
                    val requestId = accountState.googleRequestId ?: return@LaunchedEffect
                    try {
                        when (val result = requestGoogleToken()) {
                            is GoogleTokenResult.Success -> accountViewModel.googleTokenReceived(requestId, result.idToken)
                            is GoogleTokenResult.Failure -> accountViewModel.googleReauthenticationFailed(requestId, result.message)
                            GoogleTokenResult.Cancelled -> accountViewModel.googleReauthenticationCancelled(requestId)
                        }
                    } finally {
                        accountViewModel.googleReauthenticationCancelled(requestId)
                    }
                }
                val practiceState by practiceViewModel.state.collectAsStateWithLifecycle()
                val interviewState by interviewViewModel.state.collectAsStateWithLifecycle()
                LaunchedEffect(interviewState.page) {
                    if (interviewViewModel.takeContentRefresh()) viewModel.refreshAfterPractice()
                }
                val screenStateHolder = rememberSaveableStateHolder()
                LaunchedEffect(practiceState.result?.id) {
                    if (practiceViewModel.takeContentRefresh()) viewModel.refreshAfterPractice()
                }
                LaunchedEffect(profileState.page) {
                    if (profileState.page == ProfilePage.ACCOUNT) {
                        accountViewModel.open((profileState.profile as? ContentViewState.Ready)?.content)
                    }
                }
                LaunchedEffect(Unit) {
                    lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                        launch { viewModel.uiEventsFlow.collect(::showEvent) }
                        launch { profileViewModel.events.collect(::showEvent) }
                        launch { accountViewModel.events.collect(::showEvent) }
                        launch { practiceViewModel.events.collect(::showEvent) }
                        launch { interviewViewModel.events.collect(::showEvent) }
                    }
                }
                LaunchedEffect(state.sessionExpired, profileState.sessionExpired, profileState.signedOut, accountState.sessionExpired, practiceState.sessionExpired, interviewState.sessionExpired) {
                    if ((state.sessionExpired || profileState.sessionExpired || profileState.signedOut || accountState.sessionExpired || practiceState.sessionExpired || interviewState.sessionExpired) && !isFinishing) {
                        viewModel.stopSessionWork()
                        practiceViewModel.stopSessionWork()
                        interviewViewModel.stopSessionWork()
                        startActivity(Intent(this@HomeActivity, AuthActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        })
                        finish()
                    }
                }
                val profileOpen = profileState.page != ProfilePage.CLOSED
                val back: () -> Unit = {
                    if (profileState.page == ProfilePage.ACCOUNT) {
                        if (accountViewModel.back()) {
                            profileViewModel.finishAccount(
                                (accountViewModel.state.value.profile as? ContentViewState.Ready)?.content,
                                accountViewModel.state.value.changed)
                            accountViewModel.acknowledgeChanges()
                        }
                    } else if (profileViewModel.back()) viewModel.reloadAfterProfileChange()
                }
                val closePractice: () -> Unit = {
                    if (practiceState.isOpen && !practiceState.isBusy) {
                        practiceState.question?.takeIf { state.questionDetail?.question?.id != it.id }
                            ?.let(viewModel::openQuestion)
                        practiceViewModel.close()
                    }
                }
                val backPractice: () -> Unit = {
                    if (practiceState.page == PracticePage.ANSWERING) practiceViewModel.back()
                    else closePractice()
                }
                BackHandler(enabled = state.paywallOpen || practiceState.isOpen || interviewState.isOpen || profileOpen || state.questionDetail != null || state.tab != HomeTab.HOME) {
                    when {
                        state.paywallOpen -> viewModel.closePaywall()
                        practiceState.isOpen -> backPractice()
                        interviewState.isOpen -> interviewViewModel.close()
                        profileOpen -> back()
                        state.questionDetail != null -> viewModel.closeQuestion()
                        else -> viewModel.selectTab(HomeTab.HOME)
                    }
                }
                AnimatedContent(targetState = when {
                    state.paywallOpen -> "paywall"
                    practiceState.isOpen -> "practice"
                    interviewState.isOpen -> "interview"
                    profileState.page == ProfilePage.CLOSED -> "home"
                    profileState.page == ProfilePage.ACCOUNT -> "account"
                    else -> "profile"
                }, modifier = Modifier.fillMaxSize().background(AppTheme.colors.background),
                    contentAlignment = Alignment.Center,
                    transitionSpec = { fadeIn(tween(250)).togetherWith(fadeOut(tween(250))).using(null) },
                    label = "profile navigation") { destination ->
                    screenStateHolder.SaveableStateProvider(destination) {
                        if (destination == "paywall") PaywallScreen(
                            onClose = viewModel::closePaywall,
                            onUnavailable = { showEvent(UiEvent.Toast(getString(R.string.paywall_coming_soon))) }
                        ) else if (destination == "practice") PracticeScreens(
                            state = practiceState,
                            onBack = backPractice,
                            onClose = closePractice,
                            onGrade = practiceViewModel::selectGrade,
                            onContinue = practiceViewModel::continueToAnswer,
                            onMode = practiceViewModel::setMode,
                            onAnswer = practiceViewModel::updateAnswer,
                            onEvaluate = practiceViewModel::evaluateAnswer,
                            onPlans = viewModel::openPaywall,
                            onRecordingStarted = practiceViewModel::recordingStarted,
                            onTranscribing = practiceViewModel::transcriptionStarted,
                            onRecordingFinished = practiceViewModel::recordingFinished,
                            onVoiceLevel = practiceViewModel::updateVoiceLevel
                        ) else if (destination == "interview") InterviewScreens(
                            interviewState,
                            InterviewActions(
                                close = interviewViewModel::close,
                                grade = interviewViewModel::selectGrade,
                                create = interviewViewModel::create,
                                retry = interviewViewModel::retry,
                                evaluate = interviewViewModel::evaluate,
                                next = interviewViewModel::next,
                                finish = interviewViewModel::finish,
                                plans = viewModel::openPaywall,
                                retrySummary = interviewViewModel::loadSummary,
                                mode = interviewViewModel::setMode,
                                answer = interviewViewModel::updateAnswer,
                                recordingStarted = interviewViewModel::recordingStarted,
                                transcribing = interviewViewModel::transcriptionStarted,
                                recordingFinished = interviewViewModel::recordingFinished,
                                level = interviewViewModel::voiceLevel,
                                topic = { id, name -> interviewViewModel.close(); viewModel.openQuestionTopic(id, name) },
                                weak = { interviewViewModel.close(); viewModel.openWeakTopics() },
                                due = {
                                    interviewViewModel.close()
                                    viewModel.selectFavoritesFilter(com.oniolla.interviewer.domain.FavoritesFilter.DUE)
                                    viewModel.selectTab(HomeTab.FAVORITES)
                                }
                            )
                        ) else if (destination == "account") AccountScreens(
                            state = accountState,
                            onBack = back,
                            onEdit = accountViewModel::edit,
                            onUpdate = accountViewModel::update,
                            onFocused = accountViewModel::clearError,
                            onSubmit = accountViewModel::submit,
                            onResend = accountViewModel::resend,
                            onDismissResendPassword = accountViewModel::dismissResendPassword,
                            onChangeEmailAddress = accountViewModel::changeEmailAddress,
                            onPaste = accountViewModel::pasteCode,
                            onRetryProfile = accountViewModel::loadProfile
                        ) else if (destination == "profile") ProfileScreens(
                            state = profileState,
                            onBack = back,
                            onRetry = profileViewModel::loadProfile,
                            onSpecialization = profileViewModel::editSpecialization,
                            onLanguage = profileViewModel::editLanguage,
                            onTheme = profileViewModel::showThemeDialog,
                            onDismissTheme = profileViewModel::dismissThemeDialog,
                            onSelectTheme = profileViewModel::selectTheme,
                            onSignOut = {
                                viewModel.stopSessionWork()
                                profileViewModel.signOut()
                            },
                            onSelectSpecialization = profileViewModel::selectSpecialization,
                            onSaveSpecialization = profileViewModel::saveSpecialization,
                            onSelectLanguage = profileViewModel::selectLanguage,
                            onSaveLanguage = profileViewModel::saveLanguage,
                            onRetryLanguages = profileViewModel::loadLanguages,
                            onAccount = {
                                accountViewModel.open((profileState.profile as? ContentViewState.Ready)?.content)
                                profileViewModel.openAccount()
                            },
                            onRetryUsage = { profileViewModel.loadUsage() },
                            onUpgrade = viewModel::openPaywall
                        ) else HomeScreen(
                            state = state,
                            onTabSelected = viewModel::selectTab,
                            onTopicQuestions = viewModel::openTopicQuestions,
                            onQuestionSearch = viewModel::updateQuestionSearch,
                            onRetryDashboard = viewModel::loadDashboard,
                            onRetryTopics = viewModel::loadTopics,
                            onQuestionTopicSelected = viewModel::selectQuestionTopic,
                            onOpenQuestionTopic = viewModel::openQuestionTopic,
                            onQuestionDifficultySelected = viewModel::selectQuestionDifficulty,
                            onClearQuestionTopic = viewModel::clearQuestionTopic,
                            onRetryQuestions = viewModel::loadQuestions,
                            onLoadMoreQuestions = viewModel::loadMoreQuestions,
                            onRetryQuestionPage = viewModel::retryQuestionPage,
                            onTopicsFilterSelected = viewModel::selectTopicsFilter,
                            onSeeWeakTopics = viewModel::openWeakTopics,
                            onRetryTopicCatalog = viewModel::refreshTopicCatalog,
                            onToggleQuestionSaved = viewModel::toggleQuestionSaved,
                            onFavoritesFilterSelected = viewModel::selectFavoritesFilter,
                            onRetryFavorites = viewModel::loadFavorites,
                            onLoadMoreFavorites = viewModel::loadMoreFavorites,
                            onRetryFavoritePage = viewModel::retryFavoritePage,
                            onRetryStats = viewModel::loadStats,
                            onStatsWeekChanged = viewModel::changeStatsWeek,
                            onOpenQuestion = viewModel::openQuestion,
                            onCloseQuestion = viewModel::closeQuestion,
                            onRevealAnswer = viewModel::revealQuestionAnswer,
                            onRetryQuestionUsage = viewModel::refreshQuestionUsage,
                            onPro = viewModel::openPaywall,
                            onStartInterview = interviewViewModel::open,
                            interviewHistory = interviewState.history,
                            onPractice = {
                                state.questionDetail?.takeIf { it.canPractice }?.question?.let(practiceViewModel::open)
                            },
                            onProfileClicked = {
                                profileViewModel.open((state.dashboard as? ContentViewState.Ready)?.content?.profile)
                            }
                        )
                    }
                }
            }
        }
    }

    private fun showEvent(event: UiEvent) {
        if (event is UiEvent.Toast) Toast.makeText(this, event.message, Toast.LENGTH_SHORT).show()
    }
}
