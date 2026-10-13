package com.oniolla.interviewer.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oniolla.interviewer.LinkText
import com.oniolla.interviewer.R
import com.oniolla.interviewer.data.model.TopicResponse
import com.oniolla.interviewer.ui.theme.AppTheme

@Composable
internal fun QuestionTopicPicker(
    selected: QuestionTopicFilter?,
    catalog: ContentViewState<List<TopicResponse>>,
    onSelect: (Long, String) -> Unit,
    onClear: () -> Unit,
    onRetry: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp).heightIn(min = 24.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f, fill = false)) {
            Surface(
                modifier = Modifier.height(24.dp),
                onClick = { focus.clearFocus(); keyboard?.hide(); expanded = true },
                shape = RoundedCornerShape(6.dp),
                color = Color.Transparent
            ) {
                Row(Modifier.padding(vertical = 6.dp, horizontal = 2.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.questions_topic_filter,
                        selected?.name ?: stringResource(R.string.all_topics)),
                        Modifier.weight(1f, fill = false),
                        fontSize = 9.sp, lineHeight = 12.sp,
                        color = AppTheme.colors.secondaryText,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.width(5.dp))
                    FigmaIcon("chevron", Modifier.size(12.dp).rotate(90f), AppTheme.colors.secondaryText)
                }
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.width(230.dp).heightIn(max = 320.dp),
                shape = RoundedCornerShape(14.dp),
                containerColor = AppTheme.colors.container,
                border = BorderStroke(1.dp, AppTheme.colors.stroke)
            ) {
                QuestionTopicsMenuContent(selected, catalog,
                    onSelect = { id, name -> expanded = false; onSelect(id, name) },
                    onClear = { expanded = false; onClear() }, onRetry = onRetry)
            }
        }
        if (selected != null) QuestionClearButton(
            stringResource(R.string.questions_clear_topic), onClear, Modifier.padding(start = 4.dp))
    }
}

@Composable
internal fun QuestionTopicsMenuContent(
    selected: QuestionTopicFilter?,
    catalog: ContentViewState<List<TopicResponse>>,
    onSelect: (Long, String) -> Unit,
    onClear: () -> Unit,
    onRetry: () -> Unit
) {
    TopicMenuItem(stringResource(R.string.all_topics), selected == null, onClear)
    when (catalog) {
        ContentViewState.NotLoaded, ContentViewState.Loading -> ShimmerAnimation {
            repeat(4) {
                Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                    ShimmerBlock(Modifier.width(130.dp).height(16.dp))
                }
            }
        }
        is ContentViewState.Ready -> {
            catalog.content.forEach { topic ->
                TopicMenuItem(topic.name, selected?.id == topic.id) { onSelect(topic.id, topic.name) }
            }
            if (catalog.content.isEmpty()) Text(stringResource(R.string.no_topics),
                Modifier.padding(16.dp), color = AppTheme.colors.secondaryText, fontSize = 11.sp)
        }
        is ContentViewState.Failed -> Column(Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(catalog.message, color = AppTheme.colors.red, fontSize = 11.sp)
            LinkText(text = stringResource(R.string.retry), onClick = onRetry)
        }
    }
}

@Composable
private fun TopicMenuItem(name: String, selected: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        text = {
            Text(name, color = if (selected) AppTheme.colors.link else AppTheme.colors.primaryText,
                fontSize = 12.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        onClick = onClick,
        trailingIcon = if (selected) ({
            Image(painterResource(R.drawable.specialization_check), null, Modifier.size(14.dp),
                colorFilter = ColorFilter.tint(AppTheme.colors.link))
        }) else null
    )
}

@Composable
internal fun QuestionClearButton(description: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(onClick = onClick, modifier = modifier.size(24.dp).semantics {
        contentDescription = description
    }, shape = CircleShape, color = Color.Transparent) {
        Box(contentAlignment = Alignment.Center) {
            Image(painterResource(R.drawable.question_clear_topic), null, Modifier.size(14.dp),
                colorFilter = ColorFilter.tint(AppTheme.colors.secondaryText))
        }
    }
}
