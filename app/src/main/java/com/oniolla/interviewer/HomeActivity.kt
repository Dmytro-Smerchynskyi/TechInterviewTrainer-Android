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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.oniolla.interviewer.data.AppPreferences
import com.oniolla.interviewer.ui.UiEvent
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
                    }
                }
                LaunchedEffect(state.sessionExpired, profileState.sessionExpired, profileState.signedOut, accountState.sessionExpired) {
                    if ((state.sessionExpired || profileState.sessionExpired || profileState.signedOut || accountState.sessionExpired) && !isFinishing) {
                        viewModel.stopSessionWork()
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
                BackHandler(enabled = profileOpen || state.tab != HomeTab.HOME) {
                    if (profileOpen) back() else viewModel.selectTab(HomeTab.HOME)
                }
                AnimatedContent(targetState = when (profileState.page) {
                    ProfilePage.CLOSED -> "home"
                    ProfilePage.ACCOUNT -> "account"
                    else -> "profile"
                }, modifier = Modifier.fillMaxSize().background(AppTheme.colors.background),
                    contentAlignment = Alignment.Center,
                    transitionSpec = { fadeIn(tween(250)).togetherWith(fadeOut(tween(250))).using(null) },
                    label = "profile navigation") { destination ->
                    if (destination == "account") AccountScreens(
                        state = accountState,
                        onBack = back,
                        onEdit = accountViewModel::edit,
                        onUpdate = accountViewModel::update,
                        onFocused = accountViewModel::clearError,
                        onSubmit = accountViewModel::submit,
                        onResend = accountViewModel::resend,
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
                        onRetryUsage = profileViewModel::loadUsage
                    ) else HomeScreen(
                        state = state,
                        onTabSelected = viewModel::selectTab,
                        onTopicQuestions = viewModel::openTopicQuestions,
                        onQuestionSearch = viewModel::updateQuestionSearch,
                        onRetryDashboard = viewModel::loadDashboard,
                        onRetryTopics = viewModel::loadTopics,
                        onQuestionTopicSelected = viewModel::selectQuestionTopic,
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
                        onProfileClicked = {
                            profileViewModel.open((state.dashboard as? ContentViewState.Ready)?.content?.profile)
                        }
                    )
                }
            }
        }
    }

    private fun showEvent(event: UiEvent) {
        if (event is UiEvent.Toast) Toast.makeText(this, event.message, Toast.LENGTH_SHORT).show()
    }
}
