package com.oniolla.interviewer.data.model

import com.google.gson.annotations.SerializedName

data class SubscriptionResponse(
    @SerializedName("id") val id: String? = null,
    @SerializedName("provider") val provider: String? = null,
    @SerializedName("productId") val productId: String? = null,
    @SerializedName("basePlanId") val basePlanId: String? = null,
    @SerializedName("status") val status: String? = null,
    @SerializedName("startedAt") val startedAt: String? = null,
    @SerializedName("plan") val plan: String = "FREE",
    @SerializedName("currentPeriodStart") val currentPeriodStart: String? = null,
    @SerializedName("currentPeriodEnd") val currentPeriodEnd: String? = null,
    @SerializedName("autoRenew") val autoRenew: Boolean = false,
    @SerializedName("billingAccountId") val billingAccountId: String? = null,
    @SerializedName("appAccountToken") val appAccountToken: String? = null,
    @SerializedName("aiEvaluations") val aiEvaluations: AiEvaluationsUsage? = null,
    @SerializedName("answerReveals") val answerReveals: AnswerRevealsUsage? = null,
    @SerializedName("questions") val questions: QuestionsUsage? = null
) {
    val isPro get() = plan.equals("PRO", true) || plan.startsWith("PRO_", true)

    fun usage(previousQuestions: QuestionsUsage? = null) =
        DashboardUsage(plan, answerReveals, aiEvaluations, questions ?: previousQuestions)
}
