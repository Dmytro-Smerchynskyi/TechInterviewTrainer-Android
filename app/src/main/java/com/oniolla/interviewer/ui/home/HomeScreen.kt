package com.oniolla.interviewer.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import com.oniolla.interviewer.data.model.QuestionResponse
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import com.oniolla.interviewer.domain.TopicsFilter
import com.oniolla.interviewer.domain.QuestionDifficultyFilter
import com.oniolla.interviewer.domain.FavoritesFilter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oniolla.interviewer.MainButton
import com.oniolla.interviewer.R
import com.oniolla.interviewer.ui.theme.AppTheme

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    state: HomeViewState,
    onTabSelected: (HomeTab) -> Unit,
    onTopicQuestions: (Long, String) -> Unit,
    onQuestionSearch: (String) -> Unit,
    onRetryDashboard: () -> Unit,
    onRetryTopics: () -> Unit,
    onQuestionTopicSelected: (Long, String) -> Unit,
    onOpenQuestionTopic: (Long, String) -> Unit,
    onQuestionDifficultySelected: (QuestionDifficultyFilter) -> Unit,
    onClearQuestionTopic: () -> Unit,
    onRetryQuestions: () -> Unit,
    onLoadMoreQuestions: () -> Unit,
    onRetryQuestionPage: () -> Unit,
    onTopicsFilterSelected: (TopicsFilter) -> Unit,
    onSeeWeakTopics: () -> Unit,
    onRetryTopicCatalog: () -> Unit,
    onToggleQuestionSaved: (Long) -> Unit,
    onFavoritesFilterSelected: (FavoritesFilter) -> Unit,
    onRetryFavorites: () -> Unit,
    onLoadMoreFavorites: () -> Unit,
    onRetryFavoritePage: () -> Unit,
    onRetryStats: () -> Unit,
    onStatsWeekChanged: (Int) -> Unit,
    onProfileClicked: () -> Unit = {},
    onOpenQuestion: (QuestionResponse) -> Unit = {},
    onCloseQuestion: () -> Unit = {},
    onRevealAnswer: () -> Unit = {},
    onRetryQuestionUsage: () -> Unit = {},
    onPractice: () -> Unit = {},
    onPro: () -> Unit = {},
    onStartInterview: () -> Unit = {},
    interviewHistory: ContentViewState<com.oniolla.interviewer.data.model.LastInterview?> = ContentViewState.Ready(null)
) {
    val stateHolder = rememberSaveableStateHolder()
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val openQuestion: (QuestionResponse) -> Unit = {
        focus.clearFocus()
        keyboard?.hide()
        onOpenQuestion(it)
    }
    AnimatedContent(
        targetState = state.questionDetail,
        contentKey = { it?.question?.id },
        modifier = Modifier.fillMaxSize().background(AppTheme.colors.background),
        transitionSpec = { fadeIn(tween(250)).togetherWith(fadeOut(tween(250))).using(null) },
        label = "question navigation"
    ) { detail ->
        if (detail != null) {
            QuestionDetailScreen(detail, onBack = onCloseQuestion, onReveal = onRevealAnswer,
                onRetryUsage = onRetryQuestionUsage, onToggleSaved = onToggleQuestionSaved, onPractice = onPractice,
                bottomBar = { HomeNavigation(state.tab, onTabSelected) })
        } else stateHolder.SaveableStateProvider("homeTabs") {
            Scaffold(
                modifier = Modifier.imePadding(),
                containerColor = AppTheme.colors.background,
                topBar = { HomeHeader(state, onProfileClicked) },
                bottomBar = {
                    if (!WindowInsets.isImeVisible) HomeNavigation(state.tab, onTabSelected)
                }
            ) { padding ->
                AnimatedContent(
                    targetState = state.tab,
                    modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding),
                    transitionSpec = {
                        val direction = if (targetState.ordinal > initialState.ordinal) 1 else -1
                        (slideInHorizontally(tween(250)) { it * direction / 5 } + fadeIn(tween(250)))
                            .togetherWith(slideOutHorizontally(tween(250)) { -it * direction / 5 } + fadeOut(tween(250)))
                    },
                    label = "home tabs"
                ) { tab ->
                    when (tab) {
                        HomeTab.HOME -> DashboardScreen(state.dashboard, onTopicQuestions,
                            onSeeTopics = onSeeWeakTopics,
                            onSeeQuestions = {
                                onFavoritesFilterSelected(FavoritesFilter.DUE)
                                onTabSelected(HomeTab.FAVORITES)
                            },
                            onRetry = onRetryDashboard, onOpenQuestion = openQuestion,
                            onStartInterview = onStartInterview, interviewHistory = interviewHistory)
                        HomeTab.TOPICS -> TopicsScreen(state.topics, state.topicsFilter, onTopicsFilterSelected, onRetryTopics) { topic ->
                            onOpenQuestionTopic(topic.id, topic.name)
                        }
                        HomeTab.QUESTIONS -> QuestionsScreen(state, onQuestionSearch, onClearQuestionTopic,
                            onRetryQuestions, onLoadMoreQuestions, onRetryQuestionPage,
                            onQuestionTopicSelected, onRetryTopicCatalog, onToggleQuestionSaved, openQuestion, onPro,
                            onDifficultySelected = onQuestionDifficultySelected)
                        HomeTab.STATS -> StatisticsScreen(state.statistics, onRetryStats,
                            onStatsWeekChanged, onOpenQuestionTopic)
                        HomeTab.FAVORITES -> FavoritesScreen(state, onFavoritesFilterSelected,
                            onRetryFavorites, onLoadMoreFavorites, onRetryFavoritePage, onToggleQuestionSaved, openQuestion, onPro)
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeHeader(state: HomeViewState, onProfileClicked: () -> Unit) {
    val title = when (state.tab) {
        HomeTab.HOME -> if (state.displayName.isBlank()) stringResource(R.string.home_greeting_loading)
            else stringResource(R.string.home_greeting, state.displayName)
        HomeTab.TOPICS -> stringResource(R.string.topics)
        HomeTab.QUESTIONS -> stringResource(R.string.question_library)
        HomeTab.STATS -> stringResource(R.string.statistics)
        HomeTab.FAVORITES -> stringResource(R.string.favorites)
    }
    val subtitle = stringResource(when (state.tab) {
        HomeTab.HOME -> R.string.home_subtitle
        HomeTab.TOPICS -> R.string.topics_subtitle
        HomeTab.QUESTIONS -> R.string.questions_subtitle
        HomeTab.STATS -> R.string.statistics_subtitle
        HomeTab.FAVORITES -> R.string.favorites_subtitle
    })
    Row(
        modifier = Modifier.fillMaxWidth().statusBarsPadding()
            .padding(start = 20.dp, end = 18.dp, top = 12.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Column(Modifier.weight(1f)) {
            if (state.tab == HomeTab.HOME && state.dashboard == ContentViewState.Loading) {
                ShimmerAnimation { ShimmerBlock(Modifier.width(160.dp).height(33.dp)) }
            } else {
                Text(title, color = AppTheme.colors.primaryText, fontSize = 26.sp,
                    lineHeight = 33.sp, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(subtitle, color = AppTheme.colors.secondaryText, fontSize = 11.sp, lineHeight = 14.sp)
        }
        val profileDescription = stringResource(R.string.profile)
        Surface(
            onClick = onProfileClicked,
            modifier = Modifier.size(34.dp).semantics { contentDescription = profileDescription },
            shape = CircleShape,
            color = AppTheme.colors.link.copy(alpha = 0.16f),
            border = BorderStroke(1.dp, AppTheme.colors.stroke)
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (state.dashboard == ContentViewState.Loading) {
                    ShimmerAnimation { ShimmerBlock(Modifier.size(22.dp), CircleShape) }
                } else Text(displayNameInitials(state.displayName),
                    color = AppTheme.colors.primaryText, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
internal fun HomeNavigation(tab: HomeTab, onSelect: (HomeTab) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().navigationBarsPadding()
            .padding(start = 14.dp, end = 14.dp, bottom = 12.dp, top = 8.dp),
        color = AppTheme.colors.navigation,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, AppTheme.colors.stroke)
    ) {
        Row(Modifier.height(56.dp).padding(horizontal = 7.dp).selectableGroup()) {
            HomeTab.entries.forEach { item ->
                val selected = tab == item
                val color by animateColorAsState(
                    if (selected) AppTheme.colors.link else AppTheme.colors.secondaryText,
                    tween(300), label = "tab color")
                Surface(
                    selected = selected,
                    onClick = { onSelect(item) },
                    modifier = Modifier.weight(1f).fillMaxHeight().semantics { role = Role.Tab },
                    shape = RoundedCornerShape(12.dp),
                    color = Color.Transparent,
                    contentColor = color
                ) {
                    Column(
                        modifier = Modifier.padding(top = 7.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        FigmaIcon(item.asset, Modifier.size(20.dp), AppTheme.colors.homeIcon)
                        Spacer(Modifier.height(6.dp))
                        Text(stringResource(item.label), color = color, fontSize = 9.sp,
                            lineHeight = 12.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1)
                        Spacer(Modifier.weight(1f))
                        Box(Modifier.padding(bottom = 3.dp).width(22.dp).height(3.dp)
                            .background(if (selected) color else Color.Transparent, RoundedCornerShape(2.dp)))
                    }
                }
            }
        }
    }
}

private val HomeTab.asset: String get() = when (this) {
    HomeTab.HOME -> "home"
    HomeTab.TOPICS -> "topics"
    HomeTab.QUESTIONS -> "questions"
    HomeTab.STATS -> "stats"
    HomeTab.FAVORITES -> "star"
}

private val HomeTab.label: Int get() = when (this) {
    HomeTab.HOME -> R.string.home
    HomeTab.TOPICS -> R.string.topics
    HomeTab.QUESTIONS -> R.string.questions
    HomeTab.STATS -> R.string.stats
    HomeTab.FAVORITES -> R.string.favorites
}

@Composable
internal fun EmptyTab(message: String, textAlign: TextAlign = TextAlign.Start) {
    Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
        Text(message, color = AppTheme.colors.secondaryText, fontSize = 12.sp, lineHeight = 18.sp,
            textAlign = textAlign)
    }
}

@Composable
internal fun ContentError(message: String, onRetry: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(R.string.content_load_failed), color = AppTheme.colors.primaryText,
            fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Text(message, color = AppTheme.colors.red, fontSize = 12.sp,
            modifier = Modifier.padding(vertical = 16.dp))
        MainButton(buttonText = stringResource(R.string.retry), onClick = onRetry)
    }
}
