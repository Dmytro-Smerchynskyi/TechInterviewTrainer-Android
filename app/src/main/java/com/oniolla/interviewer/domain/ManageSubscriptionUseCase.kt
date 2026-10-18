package com.oniolla.interviewer.domain

import android.app.Activity
import com.oniolla.interviewer.data.*
import com.oniolla.interviewer.data.billing.*
import com.oniolla.interviewer.data.model.SubscriptionResponse
import com.oniolla.interviewer.data.payload.VerifyGooglePurchasePayload
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

data class BillingOwner(val accountId: String, val sessionVersion: Long)
class PreparedSubscription(val owner: BillingOwner, val plan: ProPlan, val oldToken: String?)
data class PurchaseProcessing(val verified: Int = 0, val pending: Int = 0,
    val retryNeeded: Boolean = false, val error: String? = null)

class ManageSubscriptionUseCase @Inject constructor(
    private val usage: SessionUsageRepository,
    private val store: BillingStore,
    private val queue: PendingPurchaseStore,
    private val tokens: SessionTokenStorage
) {
    val updates get() = store.updates
    fun observeSubscription() = usage.observeSubscription()
    fun current() = usage.currentSubscription()
    private val processing = Mutex()
    private val completed = mutableSetOf<Pair<Long, String>>()
    private val rejected = mutableMapOf<Pair<Long, String>, String>()

    suspend fun refresh(): SubscriptionResponse {
        val version = tokens.snapshot().version
        val response = usage.subscription(forceRefresh = true)
        checkSession(version)
        return response
    }

    suspend fun owner(): BillingOwner {
        val version = tokens.snapshot().version
        val response = usage.subscription()
        checkSession(version)
        return BillingOwner(requireNotNull(response.billingAccountId?.takeIf { it.isNotBlank() }) {
            "Your billing account is not available. Please retry."
        }, version)
    }

    fun isCurrent(owner: BillingOwner): Boolean = tokens.snapshot().let {
        it.version == owner.sessionVersion && !it.refreshToken.isNullOrBlank() &&
            usage.currentSubscription()?.billingAccountId == owner.accountId
    }

    private fun checkSession(version: Long) {
        val snapshot = tokens.snapshot()
        if (snapshot.version != version) throw CancellationException("Billing session changed")
        if (snapshot.refreshToken.isNullOrBlank()) throw ApiException.Unauthorized(CustomError("UNAUTHORIZED", "Session expired"))
    }
    private fun checkOwner(owner: BillingOwner) {
        checkSession(owner.sessionVersion)
        if (usage.currentSubscription()?.billingAccountId != owner.accountId)
            throw CancellationException("Billing account changed")
    }

    suspend fun plans(): List<StorePlan> = store.plans()

    suspend fun prepare(plan: ProPlan): PreparedSubscription {
        refresh() // A fresh server identity is required immediately before every purchase.
        val owner = owner()
        val subscription = current()!!
        check(!subscription.isPro || subscription.provider == "GOOGLE_PLAY") {
            "Manage this subscription with its original provider."
        }
        check(!(subscription.isPro && subscription.autoRenew && subscription.basePlanId == plan.basePlanId)) {
            "You are already on this plan."
        }
        val purchases = store.purchases().filter { PRO_PRODUCT_ID in it.products }
        check(purchases.none { it.state == PurchaseState.PENDING && it.accountId == owner.accountId }) {
            "Your purchase is pending in Google Play. Complete it before starting another purchase."
        }
        val owned = purchases.filter { it.state == PurchaseState.PURCHASED &&
            (it.accountId == owner.accountId || (owner.sessionVersion to it.token) in completed) }
        val result = process(owner, owned)
        check(!result.retryNeeded && result.error == null) {
            result.error ?: "Your previous purchase is still being verified. Please retry."
        }
        checkOwner(owner)
        val verifiedSubscription = current()!!
        check(!(verifiedSubscription.isPro && verifiedSubscription.autoRenew &&
            verifiedSubscription.basePlanId == plan.basePlanId)) { "You are already on this plan." }
        check(!verifiedSubscription.isPro || owned.isNotEmpty()) {
            "Use the Google Play account that owns your subscription to change plans."
        }
        // Always fetch a fresh ProductDetails/offer before launching Google Play.
        check(store.plans().any { it.plan == plan }) { "This plan is unavailable in Google Play." }
        checkOwner(owner)
        return PreparedSubscription(owner, plan, owned.firstOrNull()?.token)
    }

    fun launch(activity: Activity, prepared: PreparedSubscription): StoreUpdate.Failed? {
        checkOwner(prepared.owner)
        return store.launch(activity, prepared.plan, prepared.owner.accountId, prepared.oldToken)
    }

    suspend fun recover(owner: BillingOwner, explicitRestore: Boolean = false,
        checkout: PreparedSubscription? = null): PurchaseProcessing {
        checkOwner(owner)
        val purchases = try { store.purchases() } catch (error: CancellationException) { throw error }
        catch (error: Exception) {
            // Persisted purchases can still be verified while Google Play is temporarily unavailable.
            if (queue.all(owner.accountId).isEmpty()) throw error
            val result = process(owner, emptyList(), includeQueued = true)
            return result.copy(retryNeeded = true, error = result.error ?: error.message)
        }
        return process(owner, purchases, selectedPlan = checkout?.plan, oldPurchaseToken = checkout?.oldToken,
            explicitRestore = explicitRestore, includeQueued = true)
    }

    suspend fun process(owner: BillingOwner, purchases: List<StorePurchase>,
        selectedPlan: ProPlan? = null, oldPurchaseToken: String? = null, explicitRestore: Boolean = false,
        includeQueued: Boolean = false): PurchaseProcessing = processing.withLock {
        checkOwner(owner)
        var pending = 0
        val queued = queue.all(owner.accountId).associateBy { it.purchaseToken }
        val toProcess = linkedMapOf<String, PendingVerification>()
        if (includeQueued) queued.values.forEach { toProcess[it.purchaseToken] = it }
        for (purchase in purchases) {
            if (PRO_PRODUCT_ID !in purchase.products) continue
            val belongs = purchase.accountId == owner.accountId || purchase.token in queued ||
                (selectedPlan != null && purchase.accountId == null)
            // Only an explicit restore may ask the backend to link a different/legacy account.
            if (!belongs && !explicitRestore) continue
            when (purchase.state) {
                PurchaseState.PENDING -> { pending++; toProcess.remove(purchase.token) }
                PurchaseState.OTHER -> toProcess.remove(purchase.token)
                PurchaseState.PURCHASED -> {
                    val basePlan = selectedPlan?.takeUnless { purchase.token == oldPurchaseToken }?.basePlanId
                        ?: queued[purchase.token]?.basePlanId
                    toProcess[purchase.token] = PendingVerification(owner.accountId, purchase.token,
                        PRO_PRODUCT_ID, basePlan, purchase.acknowledged)
                }
            }
        }
        var verified = 0
        var retry = false
        var message: String? = null
        for (purchase in toProcess.values) {
            checkOwner(owner)
            val key = owner.sessionVersion to purchase.purchaseToken
            if (!explicitRestore) {
                if (key in completed) continue
                val previousError = rejected[key]
                if (previousError != null) {
                    message = previousError
                    continue
                }
            }
            try {
                queue.put(purchase) // Durable before HTTP; app/process death can recover the purchase.
                currentCoroutineContext().ensureActive()
                checkOwner(owner)
                usage.verifyGooglePurchase(VerifyGooglePurchasePayload(purchase.purchaseToken,
                    purchase.productId, purchase.basePlanId), owner.accountId, owner.sessionVersion)
                verified++
                checkOwner(owner)
                // Only PURCHASED purchases reach this queue; never acknowledge before server verification.
                if (!purchase.acknowledged) store.acknowledge(purchase.purchaseToken)
                currentCoroutineContext().ensureActive()
                checkOwner(owner)
                queue.remove(owner.accountId, purchase.purchaseToken)
                completed.add(key)
                rejected.remove(key)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                val apiError = error.toApiException()
                if (apiError is ApiException.Unauthorized) throw apiError
                val permanent = apiError is ApiException.BadRequest || apiError is ApiException.Forbidden ||
                    apiError is ApiException.NotFound
                val failureMessage = if (permanent) apiError.message ?: "This purchase could not be verified."
                    else "Your purchase is saved. Verification or Google Play confirmation will retry automatically."
                if (permanent) {
                    queue.remove(owner.accountId, purchase.purchaseToken)
                    rejected[key] = failureMessage
                } else retry = true
                message = failureMessage
            }
        }
        PurchaseProcessing(verified, pending, retry, message)
    }

    fun close() = store.close()
}
