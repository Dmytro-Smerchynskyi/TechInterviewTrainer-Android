package com.oniolla.interviewer.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oniolla.interviewer.R
import com.oniolla.interviewer.data.model.QuestionResponse
import com.oniolla.interviewer.ui.theme.AppTheme
import com.oniolla.interviewer.ui.theme.fonts

@Composable
internal fun PracticeTextTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalTextStyle provides LocalTextStyle.current.copy(
        fontFamily = fonts, letterSpacing = 0.sp), content = content)
}

@Composable
internal fun PracticeIcon(resource: Int, size: Dp, color: Color = AppTheme.colors.info) {
    Image(painterResource(resource), null, Modifier.size(size), colorFilter = ColorFilter.tint(color))
}

@Composable
internal fun PracticeHeader(title: String, onBack: () -> Unit, enabled: Boolean = true, close: Boolean = false) {
    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = if (close) 20.dp else 12.dp,
        end = 12.dp, top = 12.dp, bottom = if (close) 10.dp else 16.dp), verticalAlignment = Alignment.CenterVertically) {
        val description = stringResource(if (close) R.string.paywall_close else R.string.profile_back)
        val back: @Composable () -> Unit = {
            Surface(onClick = onBack, enabled = enabled, modifier = Modifier.size(36.dp)
                .semantics { contentDescription = description }, shape = CircleShape, color = Color.Transparent) {
                Box(contentAlignment = Alignment.Center) {
                    if (close) PracticeIcon(R.drawable.question_clear_topic, 22.dp, AppTheme.colors.secondaryText)
                    else FigmaIcon("chevron", Modifier.size(18.dp).rotate(180f), AppTheme.colors.secondaryText)
                }
            }
        }
        if (!close) { back(); Spacer(Modifier.width(4.dp)) }
        Text(title, color = AppTheme.colors.primaryText, fontSize = 18.sp, lineHeight = 23.sp,
            fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        if (close) back()
    }
}

@Composable
internal fun PracticeCard(modifier: Modifier = Modifier, hero: Boolean = false,
    corner: Dp = 18.dp, contentPadding: PaddingValues = PaddingValues(16.dp),
    borderColor: Color? = null, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(corner),
        color = if (hero) Color.Transparent else AppTheme.colors.container,
        border = BorderStroke(1.dp, borderColor ?: if (hero && AppTheme.colors.isDark) Color(0xFF3A4F89) else AppTheme.colors.stroke)) {
        Column(Modifier.fillMaxWidth().then(if (hero) Modifier.background(Brush.horizontalGradient(
            listOf(AppTheme.colors.heroStart, AppTheme.colors.heroEnd))) else Modifier).padding(contentPadding),
            content = content)
    }
}

@Composable
internal fun PracticeChip(label: String, color: Color) {
    Surface(shape = RoundedCornerShape(11.dp), color = color.copy(alpha = 0.12f)) {
        Box(Modifier.heightIn(min = 22.dp).padding(horizontal = 10.dp, vertical = 4.dp), contentAlignment = Alignment.Center) {
            Text(label, color = color, fontSize = 9.sp, lineHeight = 12.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PracticeQuestionCard(question: QuestionResponse) {
    PracticeCard(Modifier.fillMaxWidth().heightIn(min = 170.dp), hero = true, corner = 20.dp,
        contentPadding = PaddingValues(17.dp)) {
        Text(stringResource(R.string.practice_question_label), fontSize = 10.sp, lineHeight = 13.sp,
            fontWeight = FontWeight.Medium, color = AppTheme.colors.secondaryText)
        Spacer(Modifier.height(12.dp))
        Text(question.question, color = AppTheme.colors.primaryText, fontSize = 20.sp, lineHeight = 25.sp,
            fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(14.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PracticeChip(question.topic.name, topicTint(question.topic.visual))
            PracticeChip(difficultyLabel(question.difficulty), difficultyTint(question.difficulty))
            question.tags.filter { it.isNotBlank() }.distinct().forEach { PracticeChip(it, AppTheme.colors.link) }
        }
    }
}

@Composable
internal fun PracticeInfo(title: String? = null, text: String) {
    Surface(shape = RoundedCornerShape(14.dp), color = AppTheme.colors.info.copy(alpha = 0.07f)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PracticeIcon(R.drawable.practice_ai_note, 20.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (title != null) Text(title, color = AppTheme.colors.info, fontSize = 11.sp,
                    lineHeight = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(text, color = AppTheme.colors.secondaryText, fontSize = if (title == null) 10.sp else 9.sp,
                    lineHeight = 13.sp)
            }
        }
    }
}
