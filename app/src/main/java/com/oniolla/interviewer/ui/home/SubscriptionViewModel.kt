package com.oniolla.interviewer.ui.home

import android.app.Activity
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oniolla.interviewer.data.ApiException
import com.oniolla.interviewer.data.billing.*
import com.oniolla.interviewer.data.model.SubscriptionResponse
import com.oniolla.interviewer.data.toApiException
import com.oniolla.interviewer.domain.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

enum class BillingAction { IDLE, LOADING, PREPARING, CHECKOUT, VERIFYING, RESTORING }

data class SubscriptionViewState(
    val subscription: SubscriptionResponse? = null,
    val plans: List<StorePlan> = emptyList(),
    val selected: ProPlan = ProPlan.MONTHLY,
    val action: BillingAction = BillingAction.IDLE,
    val visible: Boolean = false,
    val error: String? = null,
    val notice: String? = null,
    val hasPendingPurchase: Boolean = false,
    val verificationPending: Boolean = false,
    val launchRequestId: Long? = null,
    val sessionExpired: Boolean = false,
    val entitlementRevision: Long = 0
) {
    val isBusy get() = action != BillingAction.IDLE
    val isCurrentPlan get() = subscription?.let {
        it.isPro && it.autoRenew && it.basePlanId == selected.basePlanId
    } == true
    val canBuy get() = !isBusy && !hasPendingPurchase && !verificationPending && subscription?.billingAccountId?.isNotBlank() == true &&
        plans.any { it.plan == selected } && !isCurrentPlan
}

@HiltViewModel
class SubscriptionViewModel @Inject constructor(
    private val manage: ManageSubscriptionUseCase,
    private val savedState: SavedStateHandle
) : ViewModel() {
    private val _state = MutableStateFlow(SubscriptionViewState(selected =
        ProPlan.entries.firstOrNull { it.name == savedState.get<String>("billingPlan") } ?: ProPlan.MONTHLY))
    val state = _state.asStateFlow()
    private val mutex = Mutex()
    private var operation: Job? = null
    private var retryJob: Job? = null
    private var foreground = false
    private var stopped = false
    private var checkout: PreparedSubscription? = null
    private var sequence = 0L
    private var needsRetry = false

    init {
        viewModelScope.launch { manage.observeSubscription().collect { subscription ->
            _state.update { it.copy(subscription = subscription) }
        } }
        viewModelScope.launch { manage.updates.collect { update ->
            if (stopped) return@collect
            mutex.withLock {
                when (update) {
                    is StoreUpdate.Purchases -> {
                        val pendingCheckout = checkout
                        checkout = null
                        _state.update { it.copy(action = BillingAction.VERIFYING, launchRequestId = null, error = null) }
                        try {
                            val owner = pendingCheckout?.owner ?: manage.owner()
                            if (manage.isCurrent(owner)) {
                                apply(manage.process(owner, update.purchases,
                                    selectedPlan = pendingCheckout?.plan, oldPurchaseToken = pendingCheckout?.oldToken))
                            }
                        } catch (error: CancellationException) { throw error }
                        catch (error: Exception) { fail(error) }
                        finally { idle() }
                    }
                    StoreUpdate.Cancelled -> {
                        checkout = null
                        _state.update { it.copy(action = BillingAction.IDLE, launchRequestId = null, error = null) }
                    }
                    is StoreUpdate.Failed -> {
                        checkout = null
                        _state.update { it.copy(action = BillingAction.IDLE, launchRequestId = null, error = update.message) }
                        if (update.alreadyOwned) refresh()
                    }
                }
            }
        } }
    }

    fun setVisible(visible: Boolean) {
        _state.update { it.copy(visible = visible) }
        if (visible) refresh()
    }

    fun resume() {
        if (stopped) return
        foreground = true
        if (_state.value.launchRequestId == null) refresh()
        scheduleRetry()
    }

    fun pause() { foreground = false; retryJob?.cancel(); retryJob = null }

    fun select(plan: ProPlan) {
        if (_state.value.isBusy) return
        savedState["billingPlan"] = plan.name
        _state.update { it.copy(selected = plan, error = null) }
    }

    fun refresh() {
        if (stopped || operation?.isActive == true || _state.value.launchRequestId != null) return
        operation = viewModelScope.launch {
            mutex.withLock {
                if (stopped) return@withLock
                _state.update { it.copy(action = BillingAction.LOADING, error = null) }
                try {
                    manage.refresh()
                    val owner = manage.owner()
                    val previousCheckout = checkout
                    checkout = null
                    apply(manage.recover(owner, checkout = previousCheckout))
                    if (_state.value.visible) {
                        val plans = manage.plans()
                        _state.update { it.copy(plans = plans) }
                    }
                } catch (error: CancellationException) { throw error }
                catch (error: Exception) { fail(error) }
                finally { idle() }
            }
        }
    }

    fun buy() {
        if (!_state.value.canBuy || operation?.isActive == true || stopped) return
        val selected = _state.value.selected
        operation = viewModelScope.launch {
            mutex.withLock {
                _state.update { it.copy(action = BillingAction.PREPARING, error = null, notice = null) }
                try {
                    checkout = manage.prepare(selected)
                    _state.update { it.copy(action = BillingAction.CHECKOUT, launchRequestId = ++sequence) }
                } catch (error: CancellationException) { throw error }
                catch (error: Exception) { checkout = null; fail(error); idle() }
            }
        }
    }

    /** Activity is used only synchronously here; never retained in ViewModel or saved state. */
    fun launch(activity: Activity, requestId: Long) {
        if (stopped || requestId != _state.value.launchRequestId || activity.isDestroyed || activity.isFinishing) return
        val prepared = checkout ?: return
        _state.update { it.copy(launchRequestId = null) }
        try {
            manage.launch(activity, prepared)?.let { failure ->
                checkout = null
                _state.update { it.copy(action = BillingAction.IDLE, error = failure.message) }
                if (failure.alreadyOwned) refresh()
            }
        } catch (error: Exception) {
            checkout = null
            fail(error)
            idle()
        }
    }

    fun restore() {
        if (_state.value.isBusy || operation?.isActive == true || stopped) return
        operation = viewModelScope.launch {
            mutex.withLock {
                _state.update { it.copy(action = BillingAction.RESTORING, error = null, notice = null) }
                try {
                    manage.refresh()
                    val result = manage.recover(manage.owner(), explicitRestore = true)
                    apply(result)
                    if (result.error == null) _state.update { it.copy(notice =
                        if (result.verified > 0) "Purchases restored. Your subscription is up to date."
                        else if (result.pending > 0) "Your Google Play purchase is still pending."
                        else "No active Pro purchases were found in this Google Play account.") }
                } catch (error: CancellationException) { throw error }
                catch (error: Exception) { fail(error) }
                finally { idle() }
            }
        }
    }

    private fun apply(result: PurchaseProcessing) {
        needsRetry = result.retryNeeded
        _state.update { it.copy(error = result.error, hasPendingPurchase = result.pending > 0,
            verificationPending = result.retryNeeded,
            notice = when {
                result.pending > 0 -> "Payment is pending in Google Play. Pro activates only after payment and server verification."
                result.verified > 0 && result.error == null -> "Purchase verified. Your subscription and credits are up to date."
                else -> it.notice
            }, entitlementRevision = it.entitlementRevision + if (result.verified > 0) 1 else 0) }
        scheduleRetry()
    }

    private fun scheduleRetry() {
        if (!foreground || !needsRetry || retryJob?.isActive == true || stopped) return
        retryJob = viewModelScope.launch {
            var waitMillis = 10_000L
            while (foreground && needsRetry && !stopped) {
                delay(waitMillis)
                if (!_state.value.isBusy) refresh()
                waitMillis = (waitMillis * 2).coerceAtMost(300_000L)
            }
        }
    }

    private fun idle() {
        if (!stopped && _state.value.launchRequestId == null) _state.update { it.copy(action = BillingAction.IDLE) }
    }

    private fun fail(error: Exception) {
        val api = error.toApiException()
        _state.update { it.copy(error = api.message?.takeIf(String::isNotBlank) ?: "Unable to update your subscription. Please retry.",
            sessionExpired = it.sessionExpired || api is ApiException.Unauthorized) }
    }

    fun stop() {
        stopped = true
        foreground = false
        checkout = null
        viewModelScope.coroutineContext.cancelChildren()
        manage.close()
    }
    override fun onCleared() { manage.close(); super.onCleared() }
}
