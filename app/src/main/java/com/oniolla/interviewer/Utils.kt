package com.oniolla.interviewer

import android.util.Patterns

fun isValidEmail(email: String): Boolean {
    return Patterns.EMAIL_ADDRESS
        .matcher(email.trim())
        .matches()
}

fun isPasswordValid(password: String): Boolean {
    return password.trim().length in 8..64
}

fun isNameValid(name: String): Boolean {
    return name.trim().length in 1..20
}