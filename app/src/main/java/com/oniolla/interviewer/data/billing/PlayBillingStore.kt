package com.oniolla.interviewer.data.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import javax.inject.Inject
class PlayBillingStore @Inject constructor(@ApplicationContext context: Context) : BillingStore {
    private val events = Channel<StoreUpdate>(Channel.BUFFERED)
    override val updates = events.receiveAsFlow()
    private val connectionMutex = Mutex()
    private var details: ProductDetails? = null
    private var offers: Map<ProPlan, ProductDetails.SubscriptionOfferDetails> = emptyMap()
    private val client = BillingClient.newBuilder(context.applicationContext)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .setListener { result, purchases ->
            when (result.responseCode) {
                BillingClient.BillingResponseCode.OK -> events.trySend(StoreUpdate.Purchases(purchases.orEmpty().map(::purchase)))
                BillingClient.BillingResponseCode.USER_CANCELED -> events.trySend(StoreUpdate.Cancelled)
                else -> events.trySend(failure(result))
            }
        }.build()

    override suspend fun connect() = connectionMutex.withLock {
        if (!client.isReady) suspendCancellableCoroutine<Unit> { continuation ->
            client.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    if (!continuation.isActive) return
                    if (result.responseCode == BillingClient.BillingResponseCode.OK) continuation.resume(Unit)
                    else continuation.resumeWithException(IllegalStateException(message(result)))
                }
                override fun onBillingServiceDisconnected() {
                    if (continuation.isActive) continuation.resumeWithException(IllegalStateException("Google Play billing disconnected. Please retry."))
                }
            })
        }
    }

    override suspend fun plans(): List<StorePlan> {
        connect()
        val params = QueryProductDetailsParams.newBuilder().setProductList(listOf(
            QueryProductDetailsParams.Product.newBuilder().setProductId(PRO_PRODUCT_ID)
                .setProductType(BillingClient.ProductType.SUBS).build()
        )).build()
        return suspendCancellableCoroutine { continuation ->
            client.queryProductDetailsAsync(params) { result, response ->
                if (!continuation.isActive) return@queryProductDetailsAsync
                if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                    continuation.resumeWithException(IllegalStateException(message(result)))
                    return@queryProductDetailsAsync
                }
                details = response.productDetailsList.firstOrNull { it.productId == PRO_PRODUCT_ID }
                // Use the regular auto-renewing base plan, not an arbitrary promotional/trial offer.
                offers = ProPlan.entries.mapNotNull { plan ->
                    details?.subscriptionOfferDetails?.firstOrNull {
                        it.basePlanId == plan.basePlanId && it.offerId == null &&
                            it.pricingPhases.pricingPhaseList.any { phase ->
                                phase.recurrenceMode == ProductDetails.RecurrenceMode.INFINITE_RECURRING
                            }
                    }?.let { plan to it }
                }.toMap()
                val plans = offers.map { (plan, offer) ->
                    val phase = offer.pricingPhases.pricingPhaseList.first {
                        it.recurrenceMode == ProductDetails.RecurrenceMode.INFINITE_RECURRING
                    }
                    StorePlan(plan, phase.formattedPrice, offer.offerToken)
                }
                if (plans.isEmpty()) continuation.resumeWithException(
                    IllegalStateException("Pro plans are unavailable in Google Play. Please try again later."))
                else continuation.resume(plans)
            }
        }
    }

    override suspend fun purchases(): List<StorePurchase> {
        connect()
        return suspendCancellableCoroutine { continuation ->
            client.queryPurchasesAsync(QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.SUBS).build()) { result, purchases ->
                if (!continuation.isActive) return@queryPurchasesAsync
                if (result.responseCode == BillingClient.BillingResponseCode.OK)
                    continuation.resume(purchases.map(::purchase))
                else continuation.resumeWithException(IllegalStateException(message(result)))
            }
        }
    }

    override fun launch(activity: Activity, plan: ProPlan, accountId: String, oldPurchaseToken: String?): StoreUpdate.Failed? {
        val product = details ?: return StoreUpdate.Failed("Reload Google Play prices before purchasing.")
        val offer = offers[plan] ?: return StoreUpdate.Failed("This plan is unavailable in Google Play.")
        val item = BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(product)
            .setOfferToken(offer.offerToken)
        val flow = BillingFlowParams.newBuilder().setObfuscatedAccountId(accountId)
        if (oldPurchaseToken != null) {
            flow.setSubscriptionUpdateParams(BillingFlowParams.SubscriptionUpdateParams.newBuilder()
                .setOldPurchaseToken(oldPurchaseToken).build())
            item.setSubscriptionProductReplacementParams(
                BillingFlowParams.ProductDetailsParams.SubscriptionProductReplacementParams.newBuilder()
                    .setOldProductId(PRO_PRODUCT_ID)
                    .setReplacementMode(
                        if (plan == ProPlan.MONTHLY)
                            BillingFlowParams.ProductDetailsParams.SubscriptionProductReplacementParams.ReplacementMode.CHARGE_FULL_PRICE
                        else BillingFlowParams.ProductDetailsParams.SubscriptionProductReplacementParams.ReplacementMode.WITHOUT_PRORATION
                    ).build())
        }
        flow.setProductDetailsParamsList(listOf(item.build()))
        val result = client.launchBillingFlow(activity, flow.build())
        return if (result.responseCode == BillingClient.BillingResponseCode.OK) null else failure(result)
    }

    override suspend fun acknowledge(token: String) {
        connect()
        suspendCancellableCoroutine { continuation ->
            client.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(token).build()) { result ->
                if (!continuation.isActive) return@acknowledgePurchase
                if (result.responseCode == BillingClient.BillingResponseCode.OK) continuation.resume(Unit)
                else continuation.resumeWithException(IllegalStateException(message(result)))
            }
        }
    }

    override fun close() { details = null; offers = emptyMap(); client.endConnection() }

    private fun purchase(purchase: Purchase) = StorePurchase(purchase.purchaseToken, purchase.products,
        when (purchase.purchaseState) {
            Purchase.PurchaseState.PURCHASED -> PurchaseState.PURCHASED
            Purchase.PurchaseState.PENDING -> PurchaseState.PENDING
            else -> PurchaseState.OTHER
        }, purchase.isAcknowledged, purchase.accountIdentifiers?.obfuscatedAccountId)

    private fun failure(result: BillingResult) = StoreUpdate.Failed(message(result),
        result.responseCode == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED)

    private fun message(result: BillingResult): String = when (result.responseCode) {
        BillingClient.BillingResponseCode.BILLING_UNAVAILABLE -> "Google Play billing is unavailable on this device."
        BillingClient.BillingResponseCode.ITEM_UNAVAILABLE -> "This plan is unavailable in Google Play."
        BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> "You already own this subscription. Restore purchases to update your account."
        BillingClient.BillingResponseCode.SERVICE_DISCONNECTED,
        BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE,
        BillingClient.BillingResponseCode.NETWORK_ERROR -> "Cannot connect to Google Play. Please retry."
        BillingClient.BillingResponseCode.USER_CANCELED -> "Purchase cancelled."
        else -> "Google Play could not complete this action. Please retry."
    }
}
