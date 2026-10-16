package com.oniolla.interviewer.ui.home

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oniolla.interviewer.data.model.MeResponse
import com.oniolla.interviewer.domain.ContentResult
import com.oniolla.interviewer.domain.ManageAccountUseCase
import com.oniolla.interviewer.isNameValid
import com.oniolla.interviewer.isPasswordValid
import com.oniolla.interviewer.isValidEmail
import com.oniolla.interviewer.ui.UiEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class AccountPage { OVERVIEW, NAME, EMAIL, CODE, PASSWORD }
enum class AccountField { NAME, EMAIL, CURRENT_PASSWORD, NEW_PASSWORD, CONFIRM_PASSWORD, CODE }

data class AccountViewState(
    val page: AccountPage = AccountPage.OVERVIEW,
    val profile: ContentViewState<MeResponse> = ContentViewState.NotLoaded,
    val name: String = "",
    val email: String = "",
    val code: String = "",
    val currentPassword: String = "",
    val newPassword: String = "",
    val confirmPassword: String = "",
    val errors: Map<AccountField, String> = emptyMap(),
    val isSaving: Boolean = false,
    val isResending: Boolean = false,
    val googleRequestId: Long? = null,
    val googleError: String? = null,
    val showResendPasswordDialog: Boolean = false,
    val sessionExpired: Boolean = false,
    val changed: Boolean = false
) {
    val hasPassword get() = (profile as? ContentViewState.Ready)?.content?.linkedProviders
        ?.any { it.equals("PASSWORD", ignoreCase = true) } == true
}

@HiltViewModel
class AccountViewModel @Inject constructor(
    private val manage: ManageAccountUseCase,
    private val savedState: SavedStateHandle
) : ViewModel() {
    private val _state = MutableStateFlow(AccountViewState(
        page = AccountPage.entries.firstOrNull { it.name == savedState.get<String>("accountPage") } ?: AccountPage.OVERVIEW,
        email = savedState.get<String>("pendingNewEmail").orEmpty(),
        changed = savedState.get<Boolean>("accountChanged") ?: false
    ))
    val state = _state.asStateFlow()
    private val _events = MutableSharedFlow<UiEvent>()
    val events = _events.asSharedFlow()
    private var profileJob: Job? = null
    private data class PendingGoogleAction(val id: Long, val draft: AccountViewState, val resend: Boolean)
    private var pendingGoogleAction: PendingGoogleAction? = null
    private var googleSequence = 0L

    fun open(profile: MeResponse?) {
        if (profile != null) _state.update { it.copy(profile = ContentViewState.Ready(profile)) }
        else if (_state.value.profile !is ContentViewState.Ready) loadProfile()
    }

    fun loadProfile() {
        if (profileJob?.isActive == true) return
        _state.update { it.copy(profile = ContentViewState.Loading) }
        profileJob = viewModelScope.launch {
            val result = manage.profile()
            _state.update { it.copy(profile = result.toViewState(), sessionExpired = result == ContentResult.SessionExpired) }
            if (result is ContentResult.Error) _events.emit(UiEvent.Toast(result.message))
        }
    }

    private fun show(page: AccountPage) {
        savedState["accountPage"] = page.name
        _state.update { it.copy(page = page, errors = emptyMap(), googleError = null) }
    }

    fun edit(page: AccountPage) {
        if (_state.value.isSaving) return
        val profile = (_state.value.profile as? ContentViewState.Ready)?.content ?: return
        if (page !in listOf(AccountPage.NAME, AccountPage.EMAIL, AccountPage.PASSWORD)) return
        _state.update { it.copy(name = profile.displayName, email = "", code = "",
            currentPassword = "", newPassword = "", confirmPassword = "") }
        savedState.remove<String>("pendingNewEmail")
        show(page)
    }

    fun update(field: AccountField, value: String) {
        if (_state.value.isSaving) return
        _state.update {
            val clean = it.copy(errors = it.errors - field, googleError = null)
            when (field) {
                AccountField.NAME -> clean.copy(name = value)
                AccountField.EMAIL -> clean.copy(email = value)
                AccountField.CURRENT_PASSWORD -> clean.copy(currentPassword = value)
                AccountField.NEW_PASSWORD -> clean.copy(newPassword = value, errors = clean.errors - AccountField.CONFIRM_PASSWORD)
                AccountField.CONFIRM_PASSWORD -> clean.copy(confirmPassword = value)
                AccountField.CODE -> clean.copy(code = value.filter { c -> c in '0'..'9' }.take(6))
            }
        }
    }

    fun clearError(field: AccountField) {
        if (!_state.value.isSaving) _state.update { it.copy(errors = it.errors - field) }
    }

    fun pasteCode(text: String?) {
        val code = text?.trim()?.takeIf { it.length == 6 && it.all { c -> c in '0'..'9' } } ?: return
        update(AccountField.CODE, code)
    }

    fun submit() {
        val state = _state.value
        if (state.isSaving || state.profile !is ContentViewState.Ready) return
        val errors = mutableMapOf<AccountField, String>()
        fun password(field: AccountField, text: String) {
            if (!isPasswordValid(text)) errors[field] = "Password should be between 8 and 64 characters"
        }
        when (state.page) {
            AccountPage.NAME -> if (!isNameValid(state.name)) errors[AccountField.NAME] = "Name should be between 1 and 20 characters"
            AccountPage.EMAIL -> {
                if (!isValidEmail(state.email)) errors[AccountField.EMAIL] =
                    if (state.email.isBlank()) "Email cannot be empty" else "Invalid email format"
                if (state.hasPassword) password(AccountField.CURRENT_PASSWORD, state.currentPassword)
            }
            AccountPage.PASSWORD -> {
                if (state.hasPassword) password(AccountField.CURRENT_PASSWORD, state.currentPassword)
                password(AccountField.NEW_PASSWORD, state.newPassword)
                password(AccountField.CONFIRM_PASSWORD, state.confirmPassword)
                if (state.newPassword != state.confirmPassword) errors[AccountField.CONFIRM_PASSWORD] = "Passwords do not match"
            }
            AccountPage.CODE -> if (state.code.length != 6 || state.code.any { it !in '0'..'9' })
                errors[AccountField.CODE] = "Enter the 6-digit code"
            AccountPage.OVERVIEW -> return
        }
        _state.update { it.copy(errors = errors, googleError = null) }
        if (errors.isNotEmpty()) return
        if (!state.hasPassword && state.page in listOf(AccountPage.EMAIL, AccountPage.PASSWORD)) {
            requestGoogle(state, resend = false)
        } else {
            _state.update { it.copy(isSaving = true) }
            execute(state)
        }
    }

    private fun requestGoogle(draft: AccountViewState, resend: Boolean) {
        val id = ++googleSequence
        pendingGoogleAction = PendingGoogleAction(id, draft, resend)
        _state.update { it.copy(isSaving = true, isResending = resend, googleRequestId = id, googleError = null) }
    }

    fun googleTokenReceived(requestId: Long, idToken: String) {
        val pending = pendingGoogleAction?.takeIf { it.id == requestId } ?: return
        if (idToken.isBlank()) {
            googleReauthenticationFailed(requestId, "Unable to verify your Google account. Please try again.")
            return
        }
        pendingGoogleAction = null
        _state.update { it.copy(googleRequestId = null) }
        execute(pending.draft, pending.resend, idToken)
    }

    fun googleReauthenticationCancelled(requestId: Long) {
        if (pendingGoogleAction?.id != requestId) return
        pendingGoogleAction = null
        _state.update { it.copy(isSaving = false, isResending = false, googleRequestId = null) }
    }

    fun googleReauthenticationFailed(requestId: Long, message: String) {
        if (pendingGoogleAction?.id != requestId) return
        googleReauthenticationCancelled(requestId)
        _state.update { it.copy(googleError = message) }
        viewModelScope.launch { _events.emit(UiEvent.Toast(message)) }
    }

    private fun passwordError(result: com.oniolla.interviewer.domain.AccountMutationResult) {
        result.currentPasswordError?.let { message ->
            _state.update { it.copy(errors = it.errors + (AccountField.CURRENT_PASSWORD to message)) }
        }
    }

    private fun execute(state: AccountViewState, resend: Boolean = false, idToken: String? = null) {
        viewModelScope.launch {
            if (resend) {
                val result = manage.resendEmail(state.email,
                    currentPassword = state.currentPassword.takeIf { state.hasPassword }, idToken = idToken)
                passwordError(result)
                if (result.currentPasswordError != null) _state.update { it.copy(showResendPasswordDialog = true) }
                when (val outcome = result.result) {
                    is ContentResult.Success -> _state.update { it.copy(isSaving = false, isResending = false, code = "",
                        currentPassword = "", newPassword = "", confirmPassword = "") }
                    else -> failed(outcome, showCodeError = result.currentPasswordError == null)
                }
            } else when (state.page) {
                AccountPage.NAME -> handleName(manage.name(state.name.trim()))
                AccountPage.EMAIL -> {
                    val result = manage.requestEmail(state.email.trim(),
                        currentPassword = state.currentPassword.takeIf { state.hasPassword }, idToken = idToken)
                    passwordError(result)
                    handleRequest(result.result, state.email.trim())
                }
                AccountPage.CODE -> handleCredentials(manage.confirmEmail(state.email, state.code), state)
                AccountPage.PASSWORD -> {
                    if (state.hasPassword) {
                        val result = manage.changePassword(state.currentPassword, state.newPassword)
                        passwordError(result)
                        handleCredentials(result.result, state)
                    } else handleCredentials(manage.setPassword(state.newPassword, requireNotNull(idToken)), state)
                }
                AccountPage.OVERVIEW -> Unit
            }
        }
    }

    private suspend fun handleName(result: ContentResult<MeResponse>) {
        when (result) {
            is ContentResult.Success -> {
                _state.update { it.copy(profile = ContentViewState.Ready(result.content)) }
                saved()
            }
            else -> failed(result)
        }
    }

    private suspend fun handleRequest(result: ContentResult<Unit>, email: String) {
        when (result) {
            is ContentResult.Success -> {
                savedState["pendingNewEmail"] = email
                _state.update { it.copy(email = email, code = "", isSaving = false,
                    currentPassword = "", newPassword = "", confirmPassword = "") }
                show(AccountPage.CODE)
            }
            else -> failed(result)
        }
    }

    private suspend fun handleCredentials(result: ContentResult<Unit>, draft: AccountViewState) {
        when (result) {
            is ContentResult.Success -> {
                val old = (draft.profile as ContentViewState.Ready).content
                val updated = if (draft.page == AccountPage.CODE) old.copy(email = draft.email, emailVerified = true)
                    else old.copy(linkedProviders = (old.linkedProviders + "PASSWORD").distinct())
                _state.update { it.copy(profile = ContentViewState.Ready(updated),
                    currentPassword = "", newPassword = "", confirmPassword = "") }
                // The mutation already succeeded: a subsequent /me failure must never resubmit it.
                val profile = manage.refreshProfile()
                if (profile is ContentResult.Success) {
                    _state.update { it.copy(profile = ContentViewState.Ready(profile.content)) }
                }
                saved()
                if (profile == ContentResult.SessionExpired) _state.update { it.copy(sessionExpired = true) }
                if (profile is ContentResult.Error) _events.emit(UiEvent.Toast(profile.message))
            }
            else -> failed(result)
        }
    }

    private fun saved() {
        savedState["accountChanged"] = true
        savedState.remove<String>("pendingNewEmail")
        _state.update { it.copy(isSaving = false, isResending = false, changed = true, errors = emptyMap(),
            code = "", currentPassword = "", newPassword = "", confirmPassword = "") }
        show(AccountPage.OVERVIEW)
    }

    private suspend fun failed(result: ContentResult<*>, showCodeError: Boolean = true) {
        _state.update { it.copy(isSaving = false, isResending = false,
            sessionExpired = result == ContentResult.SessionExpired,
            googleError = (result as? ContentResult.Error)?.message.takeIf { _ ->
                !it.hasPassword && it.page in listOf(AccountPage.EMAIL, AccountPage.PASSWORD, AccountPage.CODE)
            },
            errors = if (showCodeError && result is ContentResult.Error && it.page == AccountPage.CODE)
                it.errors + (AccountField.CODE to result.message) else it.errors) }
        if (result is ContentResult.Error) _events.emit(UiEvent.Toast(result.message))
    }

    fun resend() {
        val state = _state.value
        if (state.page != AccountPage.CODE || state.isSaving || state.profile !is ContentViewState.Ready) return
        _state.update { it.copy(googleError = null) }
        if (!state.hasPassword) {
            requestGoogle(state, resend = true)
            return
        }
        if (!state.showResendPasswordDialog) {
            _state.update { it.copy(showResendPasswordDialog = true, errors = it.errors - AccountField.CURRENT_PASSWORD) }
            return
        }
        if (!isPasswordValid(state.currentPassword)) {
            _state.update { it.copy(errors = it.errors + (AccountField.CURRENT_PASSWORD to
                "Password should be between 8 and 64 characters")) }
            return
        }
        _state.update { it.copy(isSaving = true, isResending = true, showResendPasswordDialog = false, errors = emptyMap()) }
        execute(state, resend = true)
    }

    fun dismissResendPassword() {
        if (_state.value.isSaving) return
        _state.update { it.copy(showResendPasswordDialog = false,
            currentPassword = "", newPassword = "", confirmPassword = "",
            errors = it.errors - AccountField.CURRENT_PASSWORD) }
    }

    fun changeEmailAddress() {
        if (_state.value.page == AccountPage.CODE && !_state.value.isSaving) {
            savedState.remove<String>("pendingNewEmail")
            _state.update { it.copy(code = "", currentPassword = "", showResendPasswordDialog = false) }
            show(AccountPage.EMAIL)
        }
    }

    fun acknowledgeChanges() {
        savedState["accountChanged"] = false
        _state.update { it.copy(changed = false) }
    }

    /** True only when leaving the account overview. Verification has no Back action. */
    fun back(): Boolean {
        if (_state.value.isSaving || _state.value.page == AccountPage.CODE) return false
        if (_state.value.page == AccountPage.OVERVIEW) return true
        _state.update { it.copy(currentPassword = "", newPassword = "", confirmPassword = "") }
        show(AccountPage.OVERVIEW)
        return false
    }
}
