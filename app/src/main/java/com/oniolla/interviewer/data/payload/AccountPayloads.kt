package com.oniolla.interviewer.data.payload

import com.google.gson.annotations.SerializedName

class DeleteAccountPayload(
    @SerializedName("currentPassword") val currentPassword: String? = null,
    @SerializedName("idToken") val idToken: String? = null
) {
    init {
        require((currentPassword != null) xor (idToken != null)) { "Exactly one reauthentication proof is required" }
        require((currentPassword ?: idToken).orEmpty().isNotBlank()) { "Reauthentication proof is required" }
    }
}

data class ChangeNamePayload(@SerializedName("name") val name: String)
data class ChangeEmailPayload(
    @SerializedName("newEmail") val newEmail: String,
    @SerializedName("currentPassword") val currentPassword: String? = null,
    @SerializedName("idToken") val idToken: String? = null
) {
    init { require((currentPassword != null) xor (idToken != null)) { "Exactly one reauthentication proof is required" } }
}
data class ConfirmEmailChangePayload(
    @SerializedName("newEmail") val newEmail: String,
    @SerializedName("code") val code: String
)
data class ChangePasswordPayload(
    @SerializedName("currentPassword") val currentPassword: String,
    @SerializedName("newPassword") val newPassword: String
)
data class SetPasswordPayload(
    @SerializedName("password") val password: String,
    @SerializedName("idToken") val idToken: String
)
