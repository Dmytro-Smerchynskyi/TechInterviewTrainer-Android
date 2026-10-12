package com.oniolla.interviewer.ui

const val EMAIL_CODE_LENGTH = 6

fun normalizeEmailCode(value: String): String =
    value.filter { it in '0'..'9' }.take(EMAIL_CODE_LENGTH)

fun isEmailCodeValid(value: String): Boolean =
    value.length == EMAIL_CODE_LENGTH && value.all { it in '0'..'9' }
