package com.oniolla.interviewer.data.payload

import com.google.gson.annotations.SerializedName

data class ChangeNamePayload(@SerializedName("name") val name: String)
data class ChangeEmailPayload(@SerializedName("newEmail") val newEmail: String)
data class ConfirmEmailChangePayload(
    @SerializedName("newEmail") val newEmail: String,
    @SerializedName("code") val code: String
)
data class ChangePasswordPayload(
    @SerializedName("currentPassword") val currentPassword: String,
    @SerializedName("newPassword") val newPassword: String
)
data class SetPasswordPayload(@SerializedName("password") val password: String)
