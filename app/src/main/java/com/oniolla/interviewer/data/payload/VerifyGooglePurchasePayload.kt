package com.oniolla.interviewer.data.payload

class VerifyGooglePurchasePayload(
    val purchaseToken: String,
    val productId: String,
    val basePlanId: String? = null
)
