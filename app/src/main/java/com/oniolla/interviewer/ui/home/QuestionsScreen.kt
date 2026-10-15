package com.oniolla.interviewer.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oniolla.interviewer.LinkText
import com.oniolla.interviewer.R
import com.oniolla.interviewer.domain.QuestionDifficultyFilter
import com.oniolla.interviewer.data.model.QuestionResponse
import com.oniolla.interviewer.ui.theme.AppTheme
import com.oniolla.interviewer.ui.theme.fonts
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

@Composable
internal fun QuestionsScreen(
    state: HomeViewState,
    onSearch: (String) -> Unit,
    onClearTopic: () -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onRetryPage: () -> Unit,
    onSelectTopic: (Long, String) -> Unit,
    onRetryTopicCatalog: () -> Unit,
    onToggleSaved: (Long) -> Unit,
    onOpenQuestion: (QuestionResponse) -> Unit = {},
    onPro: () -> Unit = {},
    onDifficultySelected: (QuestionDifficultyFilter) -> Unit
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        QuestionsSearch(state.questionSearch, onSearch)
        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp)
                .horizontalScroll(rememberScrollState()).selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            QuestionDifficultyFilter.entries.forEach { filter ->
                val label = filter.apiValue?.let { difficultyLabel(it) }
                    ?: stringResource(R.string.topics_filter_all)
                HomeFilterChip(label, filter == state.questionDifficulty, 48.dp) {
                    onDifficultySelected(filter)
                }
            }
        }
        QuestionTopicPicker(state.questionTopic, state.topicCatalog, onSelectTopic, onClearTopic, onRetryTopicCatalog)
        AnimatedContent(
            targetState = state.questions,
            contentKey = { it::class },
            modifier = Modifier.weight(1f).fillMaxWidth(),
            transitionSpec = { fadeIn(tween(300)).togetherWith(fadeOut(tween(200))) },
            label = "questions loading"
        ) { current ->
            when (current) {
                ContentViewState.NotLoaded, ContentViewState.Loading -> QuestionsSkeleton()
                is ContentViewState.Failed -> ContentError(current.message, onRetry)
                is ContentViewState.Ready -> QuestionsList(current.content, onLoadMore, onRetryPage, onToggleSaved, onOpenQuestion = onOpenQuestion, onPro = onPro)
            }
        }
    }
}

@Composable
private fun QuestionsSearch(query: String, onSearch: (String) -> Unit) {
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val style = TextStyle(
        fontFamily = fonts, fontSize = 12.sp, lineHeight = 15.sp,
        color = AppTheme.colors.primaryText,
        platformStyle = PlatformTextStyle(includeFontPadding = false)
    )
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = AppTheme.colors.container,
        border = BorderStroke(1.dp, AppTheme.colors.stroke)
    ) {
        BasicTextField(
            value = query,
            onValueChange = onSearch,
            singleLine = true,
            textStyle = style,
            cursorBrush = SolidColor(AppTheme.colors.accent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focus.clearFocus(); keyboard?.hide() }),
            modifier = Modifier.fillMaxWidth().height(42.dp),
            decorationBox = { field ->
                Row(Modifier.fillMaxSize().padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    FigmaIcon("search", Modifier.size(20.dp), AppTheme.colors.homeIcon)
                    Spacer(Modifier.width(10.dp))
                    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        if (query.isEmpty()) Text(stringResource(R.string.questions_search_placeholder),
                            style = style, color = AppTheme.colors.primaryText.copy(alpha = 0.5f))
                        field()
                    }
                    if (query.isNotEmpty()) {
                        QuestionClearButton(stringResource(R.string.questions_clear_search), { onSearch("") },
                            Modifier.padding(start = 6.dp))
                    }
                }
            }
        )
    }
}

@Composable
internal fun QuestionsList(content: QuestionsContent, onLoadMore: () -> Unit, onRetryPage: () -> Unit,
    onToggleSaved: (Long) -> Unit, compact: Boolean = false, emptyMessage: String? = null,
    onOpenQuestion: (QuestionResponse) -> Unit = {}, onPro: () -> Unit = {}) {
    val listState = key(content.resultRevision) { rememberLazyListState() }
    val lockedCount = content.access?.takeIf { it.limitedByPlan }?.lockedCount
        ?: content.topics.filter { it.limitedByPlan }.sumOf { it.lockedCount }
    LaunchedEffect(listState, content.items.size, content.nextCursor, content.isAppending, content.appendError) {
        if (content.nextCursor == null || content.isAppending || content.appendError != null) return@LaunchedEffect
        snapshotFlow {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            listState.layoutInfo.totalItemsCount > 0 &&
                lastVisible >= (content.items.lastIndex - 3).coerceAtLeast(0)
        }.distinctUntilChanged().filter { it }.collect { onLoadMore() }
    }
    if (content.items.isEmpty() && content.nextCursor == null && lockedCount <= 0) {
        EmptyTab(emptyMessage ?: stringResource(R.string.questions_empty), androidx.compose.ui.text.style.TextAlign.Center)
        return
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(content.items, key = { it.id }) { question ->
            if (compact) FavoriteQuestionCard(question, onToggleSaved, onOpenQuestion)
            else LibraryQuestionCard(question, onToggleSaved, onOpenQuestion)
        }
        if (content.isAppending || (content.items.isEmpty() && content.nextCursor != null && content.appendError == null)) {
            item(key = "loading_next") { ShimmerAnimation {
                if (compact) FavoriteSkeletonRow() else QuestionSkeletonRow()
            } }
        }
        content.appendError?.let { message ->
            item(key = "next_page_error") {
                Column(Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.questions_more_failed),
                        color = AppTheme.colors.primaryText, fontSize = 12.sp)
                    Text(message, color = AppTheme.colors.red, fontSize = 10.sp)
                    LinkText(text = stringResource(R.string.retry), onClick = onRetryPage)
                }
            }
        }
        if (content.nextCursor == null && lockedCount > 0) {
            item(key = "locked_questions") { LockedQuestionsCard(lockedCount, onPro) }
        }
    }
}

@Composable
private fun LibraryQuestionCard(question: QuestionResponse, onToggleSaved: (Long) -> Unit,
    onOpenQuestion: (QuestionResponse) -> Unit = {}) {
    val tint = topicTint(question.topic.visual)
    Surface(
        onClick = { onOpenQuestion(question) },
        modifier = Modifier.fillMaxWidth().heightIn(min = 84.dp),
        shape = RoundedCornerShape(13.dp),
        color = AppTheme.colors.container,
        contentColor = AppTheme.colors.primaryText,
        border = BorderStroke(1.dp, AppTheme.colors.stroke)
    ) {
        Row(Modifier.padding(start = 10.dp, end = 6.dp, top = 10.dp, bottom = 7.dp),
            verticalAlignment = Alignment.Top) {
            TopicIcon(question.topic.visual, question.topic.name,
                Modifier.padding(top = 3.dp).size(38.dp), iconSize = 20.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(question.question, color = AppTheme.colors.primaryText, fontSize = 12.sp,
                    lineHeight = 15.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(6.dp))
                val minGrade = gradeLabel(question.minGrade)
                val maxGrade = gradeLabel(question.maxGrade)
                Text(if (question.minGrade == question.maxGrade) minGrade else "$minGrade – $maxGrade",
                    color = AppTheme.colors.secondaryText, fontSize = 9.sp, lineHeight = 12.sp)
                Spacer(Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = tint.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, tint.copy(alpha = 0.55f))
                ) {
                    Box(Modifier.heightIn(min = 24.dp).padding(horizontal = 9.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center) {
                        Text(question.topic.name, color = AppTheme.colors.primaryText,
                            fontSize = 10.sp, lineHeight = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            Spacer(Modifier.width(4.dp))
            Column(Modifier.width(56.dp).padding(top = 2.dp),
                horizontalAlignment = Alignment.End) {
                DifficultyPill(question.difficulty)
                Spacer(Modifier.height(19.dp))
                QuestionSaveButton(question, onToggleSaved)
            }
            Spacer(Modifier.width(4.dp))
            FigmaIcon("chevron", Modifier.padding(top = 23.dp).size(16.dp), AppTheme.colors.homeIcon)
        }
    }
}

@Composable
internal fun QuestionSaveButton(question: QuestionResponse, onToggleSaved: (Long) -> Unit,
    iconSize: androidx.compose.ui.unit.Dp = 16.dp, touchSize: androidx.compose.ui.unit.Dp = 24.dp) {
    val savedDescription = stringResource(
        if (question.saved) R.string.question_saved else R.string.question_not_saved)
    val savedAction = stringResource(
        if (question.saved) R.string.unsave_question else R.string.save_question)
    Surface(
        checked = question.saved,
        onCheckedChange = { onToggleSaved(question.id) },
        modifier = Modifier.size(touchSize).semantics {
            contentDescription = savedAction
            stateDescription = savedDescription
        },
        shape = CircleShape,
        color = Color.Transparent
    ) {
        Box(contentAlignment = Alignment.Center) {
            Crossfade(question.saved, Modifier.size(iconSize), tween(150), label = "question saved") { saved ->
                Image(
                    painterResource(if (saved) R.drawable.question_star_filled else R.drawable.question_star_outline),
                    contentDescription = null,
                    modifier = Modifier.size(iconSize),
                    colorFilter = ColorFilter.tint(AppTheme.colors.warning)
                )
            }
        }
    }
}

@Composable
internal fun difficultyLabel(difficulty: String): String = when (difficulty) {
    "EASY" -> stringResource(R.string.question_difficulty_easy)
    "MEDIUM" -> stringResource(R.string.question_difficulty_medium)
    "HARD" -> stringResource(R.string.question_difficulty_hard)
    else -> difficulty
}

@Composable
internal fun difficultyTint(difficulty: String): Color = when (difficulty) {
    "EASY" -> AppTheme.colors.success
    "MEDIUM" -> AppTheme.colors.warning
    "HARD" -> Color(0xFFF44AA7)
    else -> AppTheme.colors.secondaryText
}

@Composable
private fun DifficultyPill(difficulty: String) {
    val color = difficultyTint(difficulty)
    val label = difficultyLabel(difficulty)
    Box(Modifier.size(width = 56.dp, height = 20.dp)
        .background(color.copy(alpha = 0.12f), RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center) {
        Text(label, color = color, fontSize = 9.sp, lineHeight = 12.sp, maxLines = 1)
    }
}

@Composable
internal fun gradeLabel(grade: String): String = when (grade) {
    "JUNIOR" -> stringResource(R.string.question_grade_junior)
    "MIDDLE" -> stringResource(R.string.question_grade_middle)
    "SENIOR" -> stringResource(R.string.question_grade_senior)
    else -> grade
}

@Composable
private fun LockedQuestionsCard(count: Int, onPro: () -> Unit) {
    Surface(
        onClick = onPro,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).heightIn(min = 74.dp),
        shape = RoundedCornerShape(13.dp),
        color = AppTheme.colors.container,
        border = BorderStroke(1.dp, AppTheme.colors.stroke)
    ) {
        Row(Modifier.padding(horizontal = 15.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.questions_pro_locked, count),
                    color = AppTheme.colors.primaryText, fontSize = 11.sp, lineHeight = 14.sp,
                    fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.questions_pro_description),
                    modifier = Modifier.padding(top = 7.dp),
                    color = AppTheme.colors.secondaryText, fontSize = 8.sp, lineHeight = 11.sp)
            }
            Text(stringResource(R.string.questions_pro),
                modifier = Modifier.background(AppTheme.colors.warning.copy(alpha = 0.12f),
                    RoundedCornerShape(10.dp)).padding(horizontal = 16.dp, vertical = 5.dp),
                color = AppTheme.colors.warning, fontSize = 8.sp)
        }
    }
}

@Composable
private fun QuestionsSkeleton() {
    val description = stringResource(R.string.content_loading)
    ShimmerAnimation {
        LazyColumn(Modifier.fillMaxSize().semantics { stateDescription = description },
            contentPadding = PaddingValues(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(7) { QuestionSkeletonRow() }
        }
    }
}

@Composable
private fun QuestionSkeletonRow() {
    SkeletonSurface(Modifier.fillMaxWidth().height(84.dp), 13) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 13.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ShimmerBlock(Modifier.size(38.dp), RoundedCornerShape(10.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ShimmerBlock(Modifier.fillMaxWidth().height(12.dp))
                ShimmerBlock(Modifier.fillMaxWidth(0.8f).height(9.dp))
                ShimmerBlock(Modifier.width(52.dp).height(20.dp), RoundedCornerShape(12.dp))
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(18.dp)) {
                ShimmerBlock(Modifier.size(width = 56.dp, height = 20.dp), RoundedCornerShape(10.dp))
                ShimmerBlock(Modifier.size(16.dp), CircleShape)
            }
        }
    }
}
