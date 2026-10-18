package com.oniolla.interviewer.data.payload

import com.google.gson.annotations.SerializedName

class VerifyGooglePurchasePayload(
    @SerializedName("purchaseToken") val purchaseToken: String,
    @SerializedName("productId") val productId: String,
    @SerializedName("basePlanId") val basePlanId: String? = null
)
