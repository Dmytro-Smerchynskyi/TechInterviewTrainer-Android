package com.oniolla.interviewer.data.billing

import android.app.Activity
import kotlinx.coroutines.flow.Flow

const val PRO_PRODUCT_ID = "interview-trainer-pro"

enum class ProPlan(val basePlanId: String) { WEEKLY("weekly"), MONTHLY("monthly") }
data class StorePlan(val plan: ProPlan, val price: String, val offerToken: String)
enum class PurchaseState { PURCHASED, PENDING, OTHER }
class StorePurchase(val token: String, val products: List<String>, val state: PurchaseState,
    val acknowledged: Boolean, val accountId: String?)
sealed interface StoreUpdate {
    class Purchases(val purchases: List<StorePurchase>) : StoreUpdate
    data object Cancelled : StoreUpdate
    class Failed(val message: String, val alreadyOwned: Boolean = false) : StoreUpdate
}

interface BillingStore {
    val updates: Flow<StoreUpdate>
    suspend fun connect()
    suspend fun plans(): List<StorePlan>
    suspend fun purchases(): List<StorePurchase>
    fun launch(activity: Activity, plan: ProPlan, accountId: String, oldPurchaseToken: String?): StoreUpdate.Failed?
    suspend fun acknowledge(token: String)
    fun close()
}
