package com.oniolla.interviewer.ui

import android.content.Intent

/** Accept only our reset link. The opaque token stays in ViewModel memory after consumption. */
internal fun passwordResetToken(intent: Intent): String? = runCatching {
    if (intent.action != Intent.ACTION_VIEW) return null
    val uri = intent.data ?: return null
    if (!uri.isHierarchical || !uri.scheme.equals("https", ignoreCase = true) ||
        !uri.host.equals("interviewtrainer.app", ignoreCase = true) ||
        uri.userInfo != null || uri.port !in listOf(-1, 443) ||
        uri.encodedPath != "/resetpassword" || uri.fragment != null) return null
    uri.getQueryParameters("token").singleOrNull()
        ?.takeIf { it.isNotBlank() && it.length <= 8192 }
}.getOrNull()
