package com.oniolla.interviewer.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.expandVertically
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
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.draw.alpha
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
import com.oniolla.interviewer.data.billing.ProPlan as BillingPlan
import com.oniolla.interviewer.data.model.SubscriptionResponse
import com.oniolla.interviewer.ui.theme.AppTheme
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun PaywallScreen(
    state: SubscriptionViewState = SubscriptionViewState(),
    onClose: () -> Unit = {},
    onSelectPlan: (BillingPlan) -> Unit = {},
    onBuy: () -> Unit = {},
    onRestore: () -> Unit = {},
    onRetry: () -> Unit = {}
) {
    val scroll = rememberScrollState()
    val snackbarHost = remember { SnackbarHostState() }
    val errorMessage = state.error?.let { stringResource(it) }
    LaunchedEffect(errorMessage) {
        if (errorMessage != null) snackbarHost.showSnackbar(errorMessage, duration = SnackbarDuration.Long)
    }
    LaunchedEffect(state.visible) { if (state.visible) scroll.scrollTo(0) }
    val monthly = state.selected == BillingPlan.MONTHLY
    val selectedOffer = state.plans.firstOrNull { it.plan == state.selected }
    PracticeTextTheme {
    Scaffold(containerColor = AppTheme.colors.background,
        snackbarHost = {
            SnackbarHost(snackbarHost) { data ->
                Snackbar(data, containerColor = AppTheme.colors.container, contentColor = AppTheme.colors.primaryText)
            }
        },
        topBar = { PracticeHeader(stringResource(R.string.paywall_title), onClose, enabled = !state.isBusy, close = true) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)
            .verticalScroll(scroll).padding(horizontal = 20.dp)) {
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
            state.subscription?.let { subscription ->
                if (subscription.isPro || subscription.status != null) {
                    Spacer(Modifier.height(16.dp))
                    SubscriptionStatus(subscription)
                }
            }
            Column(Modifier.padding(top = 16.dp).selectableGroup(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                BillingPlan.entries.forEach { plan ->
                    val offer = state.plans.firstOrNull { it.plan == plan }
                    ProPlanCard(monthly = plan == BillingPlan.MONTHLY, selected = state.selected == plan,
                        price = offer?.price, enabled = !state.isBusy && offer != null,
                        current = state.subscription?.let { it.isPro && it.basePlanId == plan.basePlanId } == true,
                        onClick = { onSelectPlan(plan) })
                }
            }
            AnimatedVisibility(visible = state.error != null || state.notice != null,
                enter = fadeIn() + expandVertically()) {
                Column(Modifier.fillMaxWidth().padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    state.error?.let { Text(stringResource(it), color = AppTheme.colors.red,
                        fontSize = 11.sp, lineHeight = 16.sp) }
                    state.notice?.let { Text(it, color = AppTheme.colors.secondaryText,
                        fontSize = 11.sp, lineHeight = 16.sp) }
                    if (state.error != null) LinkText(text = stringResource(R.string.billing_retry),
                        enabled = !state.isBusy, onClick = onRetry)
                }
            }
            Spacer(Modifier.height(20.dp))
            val button = when {
                state.hasPendingPurchase -> stringResource(R.string.billing_pending)
                state.verificationPending -> stringResource(R.string.billing_verification_pending)
                state.isCurrentPlan -> stringResource(R.string.billing_current_plan)
                selectedOffer == null -> stringResource(R.string.billing_unavailable)
                state.subscription?.isPro == true -> stringResource(
                    if (monthly) R.string.billing_switch_monthly else R.string.billing_switch_weekly, selectedOffer.price)
                else -> stringResource(
                    if (monthly) R.string.billing_start_monthly else R.string.billing_start_weekly, selectedOffer.price)
            }
            MainButton(Modifier.fillMaxWidth().height(50.dp).alpha(if (!state.canBuy && !state.isBusy) 0.45f else 1f),
                onClick = onBuy, textSize = 10.sp,
                enabled = state.canBuy, isLoading = state.isBusy, buttonText = button,
                loadingButtonText = stringResource(when (state.action) {
                    BillingAction.RESTORING -> R.string.billing_restoring
                    BillingAction.VERIFYING -> R.string.billing_verifying
                    BillingAction.CHECKOUT -> R.string.billing_checkout
                    else -> R.string.billing_loading
                }))
            if (!state.isBusy && (state.plans.isEmpty() || state.hasPendingPurchase) && state.error == null) {
                Box(Modifier.fillMaxWidth().padding(top = 16.dp), contentAlignment = Alignment.Center) {
                    LinkText(text = stringResource(R.string.billing_retry), onClick = onRetry)
                }
            }
            Box(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 16.dp), contentAlignment = Alignment.Center) {
                LinkText(text = stringResource(R.string.paywall_restore), enabled = !state.isBusy, onClick = onRestore)
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
private fun ProPlanCard(monthly: Boolean, selected: Boolean, price: String?, enabled: Boolean,
    current: Boolean, onClick: () -> Unit) {
    val background by animateColorAsState(
        if (selected) AppTheme.colors.leftGradient.copy(alpha = 0.1f) else AppTheme.colors.container,
        tween(200), label = "plan background")
    val border by animateColorAsState(
        if (selected) AppTheme.colors.leftGradient else AppTheme.colors.stroke,
        tween(200), label = "plan border")
    Surface(selected = selected, onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth()
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
                    Text(price ?: stringResource(R.string.billing_price_unavailable),
                        color = AppTheme.colors.primaryText, fontSize = 21.sp, lineHeight = 25.sp, fontWeight = FontWeight.Bold)
                    Text(stringResource(if (monthly) R.string.paywall_per_month else R.string.paywall_per_week),
                        color = AppTheme.colors.secondaryText, fontSize = 8.sp, lineHeight = 11.sp)
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(if (monthly) R.string.paywall_monthly_note else R.string.paywall_weekly_note),
                    Modifier.weight(1f), color = AppTheme.colors.secondaryText, fontSize = 8.sp, lineHeight = 11.sp)
                if (monthly || current) {
                    Spacer(Modifier.width(8.dp))
                    Surface(shape = RoundedCornerShape(13.dp), color = AppTheme.colors.info.copy(alpha = 0.07f)) {
                        Text(stringResource(if (current) R.string.billing_current_plan else R.string.paywall_best_value), Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            color = AppTheme.colors.secondaryText, fontSize = 7.sp, lineHeight = 10.sp)
                    }
                }
            }
        }
    }
}

/** Both the entitlement and quotas here come from the authenticated backend response. */
@Composable
internal fun SubscriptionStatus(subscription: SubscriptionResponse) {
    Surface(shape = RoundedCornerShape(14.dp), color = AppTheme.colors.container,
        border = BorderStroke(1.dp, AppTheme.colors.stroke)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(if (subscription.isPro) R.string.billing_pro_status else R.string.billing_free_status),
                color = AppTheme.colors.primaryText, fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold)
            SubscriptionPeriodText(subscription)
            subscription.aiEvaluations?.let {
                Text(stringResource(R.string.billing_ai_remaining, it.remaining, it.limit),
                    color = AppTheme.colors.secondaryText, fontSize = 10.sp, lineHeight = 15.sp)
            }
            subscription.answerReveals?.let {
                Text(stringResource(R.string.billing_reveals_remaining, it.remaining, it.limit),
                    color = AppTheme.colors.secondaryText, fontSize = 10.sp, lineHeight = 15.sp)
            }
        }
    }
}

@Composable
internal fun SubscriptionPeriodText(subscription: SubscriptionResponse) {
    val status = when (subscription.status) {
        "ACTIVE" -> R.string.billing_active
        "CANCELED", "CANCELLED" -> R.string.billing_cancelled
        "IN_GRACE_PERIOD", "GRACE_PERIOD" -> R.string.billing_grace
        "ON_HOLD", "ACCOUNT_HOLD" -> R.string.billing_on_hold
        "EXPIRED" -> R.string.billing_expired
        "PAUSED" -> R.string.billing_paused
        "PENDING" -> R.string.billing_pending
        else -> null
    }
    status?.let { Text(stringResource(it), color = AppTheme.colors.secondaryText, fontSize = 10.sp, lineHeight = 15.sp) }
    if (subscription.isPro) {
        val date = remember(subscription.currentPeriodEnd, Locale.getDefault()) {
            subscription.currentPeriodEnd?.let { value -> runCatching {
                val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ROOT)
                parser.timeZone = java.util.TimeZone.getTimeZone("UTC")
                parser.parse(value)?.let { DateFormat.getDateInstance(DateFormat.MEDIUM).format(it) }
            }.getOrNull() }
        }
        date?.let {
            Text(stringResource(if (subscription.autoRenew) R.string.billing_renews else R.string.billing_access_until, it),
                color = AppTheme.colors.secondaryText, fontSize = 10.sp, lineHeight = 15.sp)
        }
    }
}
