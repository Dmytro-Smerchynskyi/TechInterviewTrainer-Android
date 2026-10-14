package com.oniolla.interviewer.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oniolla.interviewer.R
import com.oniolla.interviewer.data.model.DashboardUsage
import com.oniolla.interviewer.ui.theme.AccountHeroStroke
import com.oniolla.interviewer.ui.theme.AppTheme
import java.util.Locale

@Composable
internal fun ProfileUsage(
    usage: ContentViewState<DashboardUsage>,
    enabled: Boolean,
    onRetry: () -> Unit
) {
    Text(stringResource(R.string.account_plan_usage), fontSize = 15.sp, lineHeight = 19.sp,
        color = AppTheme.colors.primaryText, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 26.dp, bottom = 10.dp))
    when (usage) {
        is ContentViewState.Ready -> UsageCard(usage.content, enabled)
        is ContentViewState.Failed -> Box(Modifier.fillMaxWidth().height(150.dp)) {
            ContentError(usage.message, onRetry)
        }
        else -> ShimmerAnimation {
            SkeletonSurface(Modifier.fillMaxWidth().height(150.dp), 18) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    ShimmerBlock(Modifier.width(100.dp).height(18.dp))
                    repeat(3) { ShimmerBlock(Modifier.fillMaxWidth().height(12.dp)) }
                }
            }
        }
    }
}

@Composable
private fun UsageCard(usage: DashboardUsage, enabled: Boolean) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, AccountHeroStroke), color = Color.Transparent) {
        Column(Modifier.background(Brush.horizontalGradient(listOf(AppTheme.colors.heroStart, AppTheme.colors.heroEnd)))
            .heightIn(min = 150.dp).padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.account_plan, usage.plan.lowercase(Locale.ROOT)
                        .replaceFirstChar { it.titlecase(Locale.ROOT) }), color = AppTheme.colors.primaryText,
                        fontSize = 16.sp, lineHeight = 21.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text(if (usage.questions?.unlimited == true) stringResource(R.string.account_unlimited_questions)
                        else usage.questions?.limitPerTopic?.let { stringResource(R.string.account_questions_per_topic, it) }
                            ?: stringResource(R.string.stats_unavailable),
                        fontSize = 9.sp, lineHeight = 12.sp, color = AppTheme.colors.secondaryText)
                }
                Surface(onClick = {}, enabled = enabled, modifier = Modifier.width(120.dp).height(32.dp),
                    shape = RoundedCornerShape(13.dp), color = AppTheme.colors.container,
                    border = BorderStroke(1.dp, AppTheme.colors.stroke)) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.account_upgrade), fontSize = 9.sp,
                            color = AppTheme.colors.primaryText, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            UsageLine(stringResource(R.string.account_ai_evaluations),
                usage.aiEvaluations?.let { stringResource(R.string.account_remaining, it.remaining, it.limit) }
                    ?: stringResource(R.string.stats_unavailable))
            Spacer(Modifier.height(12.dp))
            UsageLine(stringResource(R.string.account_reference_answers),
                usage.answerReveals?.let { stringResource(R.string.account_remaining, it.remaining, it.limit) }
                    ?: stringResource(R.string.stats_unavailable))
        }
    }
}

@Composable
private fun UsageLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 9.sp, lineHeight = 12.sp, color = AppTheme.colors.secondaryText)
        Text(value, fontSize = 9.sp, lineHeight = 12.sp, color = AppTheme.colors.secondaryText)
    }
}

