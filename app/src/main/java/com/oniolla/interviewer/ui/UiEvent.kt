package com.oniolla.interviewer.ui

sealed interface UiEvent {
    data class Toast(val message: String) : UiEvent
}