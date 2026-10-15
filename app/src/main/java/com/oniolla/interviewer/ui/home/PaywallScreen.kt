package com.oniolla.interviewer.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oniolla.interviewer.LinkText
import com.oniolla.interviewer.MainButton
import com.oniolla.interviewer.R
import com.oniolla.interviewer.ui.theme.AppTheme

@Composable
fun PaywallScreen(onClose: () -> Unit = {}, onUnavailable: () -> Unit = {}) {
    var monthly by rememberSaveable { mutableStateOf(true) }
    PracticeTextTheme {
    Scaffold(containerColor = AppTheme.colors.background,
        topBar = { PracticeHeader(stringResource(R.string.paywall_title), onClose, close = true) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)
            .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            PracticeCard(Modifier.fillMaxWidth().heightIn(min = 150.dp), hero = true, corner = 20.dp,
                borderColor = AppTheme.colors.stroke) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    PracticeIcon(R.drawable.specialization_grade, 34.dp)
                    Spacer(Modifier.weight(1f))
                    Surface(shape = RoundedCornerShape(13.dp), color = AppTheme.colors.info.copy(alpha = 0.07f)) {
                        Text(stringResource(R.string.paywall_choose), Modifier.padding(horizontal = 16.dp, vertical = 7.dp),
                            color = AppTheme.colors.secondaryText, fontSize = 9.sp, lineHeight = 12.sp)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.paywall_hero), color = AppTheme.colors.primaryText, fontSize = 21.sp,
                    lineHeight = 26.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(7.dp))
                Text(stringResource(R.string.paywall_free), color = AppTheme.colors.secondaryText, fontSize = 9.sp, lineHeight = 13.sp)
            }
            Text(stringResource(R.string.paywall_unlocks), Modifier.padding(top = 18.dp, bottom = 12.dp),
                color = AppTheme.colors.primaryText, fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ProFeature(stringResource(R.string.paywall_all_questions), stringResource(R.string.paywall_all_questions_description))
                ProFeature(stringResource(R.string.paywall_reveals), stringResource(R.string.paywall_reveals_description))
                ProFeature(stringResource(R.string.paywall_coach), stringResource(R.string.paywall_coach_description))
            }
            Column(Modifier.padding(top = 16.dp).selectableGroup(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ProPlan(monthly = false, selected = !monthly) { monthly = false }
                ProPlan(monthly = true, selected = monthly) { monthly = true }
            }
            Spacer(Modifier.height(20.dp))
            MainButton(Modifier.fillMaxWidth().height(50.dp), onClick = onUnavailable, textSize = 10.sp,
                buttonText = stringResource(if (monthly) R.string.paywall_start_monthly else R.string.paywall_start_weekly))
            Box(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 16.dp), contentAlignment = Alignment.Center) {
                LinkText(text = stringResource(R.string.paywall_restore), onClick = onUnavailable)
            }
            Text(stringResource(R.string.paywall_terms), Modifier.fillMaxWidth().padding(bottom = 24.dp),
                color = AppTheme.colors.secondaryText, fontSize = 8.sp, lineHeight = 11.sp, textAlign = TextAlign.Center)
        }
    }
    }
}

@Composable
private fun ProFeature(title: String, subtitle: String) {
    Surface(shape = RoundedCornerShape(13.dp), color = AppTheme.colors.container,
        border = BorderStroke(1.dp, AppTheme.colors.stroke)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 50.dp).padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(24.dp).background(AppTheme.colors.leftGradient.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center) {
                PracticeIcon(R.drawable.specialization_check, 14.dp, AppTheme.colors.leftGradient)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(title, color = AppTheme.colors.primaryText, fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = AppTheme.colors.secondaryText, fontSize = 8.sp, lineHeight = 11.sp)
            }
        }
    }
}

@Composable
private fun ProPlan(monthly: Boolean, selected: Boolean, onClick: () -> Unit) {
    val background by animateColorAsState(
        if (selected) AppTheme.colors.leftGradient.copy(alpha = 0.1f) else AppTheme.colors.container,
        tween(200), label = "plan background")
    val border by animateColorAsState(
        if (selected) AppTheme.colors.leftGradient else AppTheme.colors.stroke,
        tween(200), label = "plan border")
    Surface(selected = selected, onClick = onClick, modifier = Modifier.fillMaxWidth()
        .heightIn(min = if (monthly) 104.dp else 94.dp).semantics { role = Role.RadioButton },
        shape = RoundedCornerShape(16.dp),
        color = background, border = BorderStroke(1.dp, border)) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(if (monthly) R.string.paywall_monthly else R.string.paywall_weekly),
                        color = AppTheme.colors.primaryText, fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Text(stringResource(if (monthly) R.string.paywall_monthly_allowance else R.string.paywall_weekly_allowance),
                        color = AppTheme.colors.secondaryText, fontSize = 8.sp, lineHeight = 11.sp)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(stringResource(if (monthly) R.string.paywall_monthly_price else R.string.paywall_weekly_price),
                        color = AppTheme.colors.primaryText, fontSize = 21.sp, lineHeight = 25.sp, fontWeight = FontWeight.Bold)
                    Text(stringResource(if (monthly) R.string.paywall_per_month else R.string.paywall_per_week),
                        color = AppTheme.colors.secondaryText, fontSize = 8.sp, lineHeight = 11.sp)
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(if (monthly) R.string.paywall_monthly_note else R.string.paywall_weekly_note),
                    Modifier.weight(1f), color = AppTheme.colors.secondaryText, fontSize = 8.sp, lineHeight = 11.sp)
                if (monthly) {
                    Spacer(Modifier.width(8.dp))
                    Surface(shape = RoundedCornerShape(13.dp), color = AppTheme.colors.info.copy(alpha = 0.07f)) {
                        Text(stringResource(R.string.paywall_best_value), Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            color = AppTheme.colors.secondaryText, fontSize = 7.sp, lineHeight = 10.sp)
                    }
                }
            }
        }
    }
}
