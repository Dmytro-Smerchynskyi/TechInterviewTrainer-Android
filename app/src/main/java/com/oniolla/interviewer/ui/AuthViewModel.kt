package com.oniolla.interviewer.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oniolla.interviewer.data.AuthState
import com.oniolla.interviewer.data.payload.LoginPayload
import com.oniolla.interviewer.data.payload.SignupPayload
import com.oniolla.interviewer.data.toApiException
import com.oniolla.interviewer.data.toAuthState
import com.oniolla.interviewer.domain.ForgotPasswordUseCase
import com.oniolla.interviewer.domain.LoginUseCase
import com.oniolla.interviewer.domain.RestoreSessionUseCase
import com.oniolla.interviewer.domain.SignupUseCase
import com.oniolla.interviewer.isNameValid
import com.oniolla.interviewer.isPasswordValid
import com.oniolla.interviewer.isValidEmail
import com.oniolla.interviewer.ui.AuthViewModel.AuthViewState
import dagger.hilt.android.lifecycle.HiltViewModel
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.schedulers.Schedulers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val restoreSessionUseCase: RestoreSessionUseCase,
    private val loginUseCase: LoginUseCase,
    private val signupUseCase: SignupUseCase,
    private val forgotPasswordUseCase: ForgotPasswordUseCase
) : ViewModel() {

    private val _stateFlow: MutableStateFlow<AuthViewState> = MutableStateFlow(
        AuthViewState.Loading(
            0f
        )
    )
    val stateFlow = _stateFlow.asStateFlow()

    private val _uiEventsFlow: MutableSharedFlow<UiEvent> = MutableSharedFlow()
    val uiEventsFlow = _uiEventsFlow.asSharedFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            restoreSessionUseCase(Unit)
                .map { it.toUi() }
                .collect {
                    _stateFlow.value = it
                }
        }
    }

    fun showLogin() {
        if ((_stateFlow.value as? AuthViewState.Unauthorized.ForgotPassword)?.isLoading == true) return
        _stateFlow.value = AuthViewState.Unauthorized.Login()
    }

    fun showSignup() {
        _stateFlow.value = AuthViewState.Unauthorized.SignUp()
    }

    fun showForgotPassword() {
        if ((_stateFlow.value as? AuthViewState.Unauthorized.Login)?.isLoading == true) return
        _stateFlow.value = AuthViewState.Unauthorized.ForgotPassword()
    }

    fun forgotPassword(email: String) {
        val state = _stateFlow.value as? AuthViewState.Unauthorized.ForgotPassword ?: return
        if (state.isLoading) return

        if (!isValidEmail(email)) {
            _stateFlow.value = state.copy(
                emailError = if (email.trim().isBlank()) "Email cannot be empty" else "Invalid email format"
            )
            return
        }

        _stateFlow.value = state.copy(isLoading = true, emailError = null)
        viewModelScope.launch {
            val result = forgotPasswordUseCase(email)
            when (val viewState = result.toUi()) {
                is AuthViewState.Error -> {
                    _stateFlow.value = state.copy(emailError = null)
                    _uiEventsFlow.emit(UiEvent.Toast(viewState.message ?: "Unknown error"))
                }

                is AuthViewState.Unauthorized.ForgotPassword -> {
                    _stateFlow.value = viewState
                    if (result is AuthState.PasswordResetLinkSent) {
                        _uiEventsFlow.emit(UiEvent.Toast(result.message))
                    }
                }

                else -> {
                    _stateFlow.value = state.copy(emailError = null)
                    _uiEventsFlow.emit(UiEvent.Toast("Unknown error"))
                }
            }
        }
    }

    fun resendPasswordResetLink() {
        val state = _stateFlow.value as? AuthViewState.Unauthorized.ForgotPassword ?: return
        state.sentEmail?.let(::forgotPassword)
    }

    fun onRecoveryEmailChanged() {
        val state = _stateFlow.value as? AuthViewState.Unauthorized.ForgotPassword ?: return
        if (!state.isLoading) {
            _stateFlow.value = state.copy(resetLinkSent = false, sentEmail = null)
        }
    }

    fun signup(name: String, email: String, password: String) {
        var localValidationPassed = true

        if (!isNameValid(name)) {
            _stateFlow.value.let { state ->
                if (state is AuthViewState.Unauthorized.SignUp) {
                    _stateFlow.value =
                        state.copy(nameError = "Name should be between 1 and 20 characters")
                }
            }
            localValidationPassed = false
        }

        if (!isValidEmail(email)) {
            _stateFlow.value.let { state ->
                if (state is AuthViewState.Unauthorized.SignUp) {
                    if (email.trim().isBlank()) {
                        _stateFlow.value = state.copy(emailError = "Email cannot be empty")
                    } else {
                        _stateFlow.value = state.copy(emailError = "Invalid email format")
                    }
                }
            }
            localValidationPassed = false
        }

        if (!isPasswordValid(password)) {
            _stateFlow.value.let { state ->
                if (state is AuthViewState.Unauthorized.SignUp) {
                    _stateFlow.value =
                        state.copy(passwordError = "Password should be between 8 and 64 characters")
                }
            }
            localValidationPassed = false
        }

        if (localValidationPassed) {
            setLoading(true)
            viewModelScope.launch {
                signupUseCase(SignupPayload(email, password, name))
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                        {
                            setLoading(false)
                            viewModelScope.launch {
                                _uiEventsFlow.emit(UiEvent.Toast(it.message))
                                _stateFlow.value = AuthViewState.VerificationEmailSent
                            }
                        },
                        {
                            setLoading(false)
                            it.toApiException().toAuthState().let { authState ->
                                if (authState is AuthState.Error) {
                                    viewModelScope.launch {
                                        _uiEventsFlow.emit(
                                            UiEvent.Toast(
                                                authState.message ?: "Unknown error"
                                            )
                                        )
                                    }
                                }
                            }
                        })
            }
        }
    }

    fun login(email: String, password: String) {
        var localValidationPassed = true

        if (!isValidEmail(email)) {
            _stateFlow.value.let { state ->
                if (state is AuthViewState.Unauthorized.Login) {
                    if (email.trim().isBlank()) {
                        _stateFlow.value = state.copy(emailError = "Email cannot be empty")
                    } else {
                        _stateFlow.value = state.copy(emailError = "Invalid email format")
                    }
                }
            }
            localValidationPassed = false
        }

        if (!isPasswordValid(password)) {
            _stateFlow.value.let { state ->
                if (state is AuthViewState.Unauthorized.Login) {
                    _stateFlow.value =
                        state.copy(passwordError = "Password should be between 8 and 64 characters")
                }
            }
            localValidationPassed = false
        }

        if (localValidationPassed) {
            setLoading(true)
            viewModelScope.launch {
                loginUseCase(LoginPayload(email, password)).toUi().let { state ->
                    setLoading(false)
                    when (state) {
                        is AuthViewState.Error ->
                            _uiEventsFlow.emit(
                                UiEvent.Toast(
                                    state.message ?: "Unknown error"
                                )
                            )

                        else -> _stateFlow.value = state
                    }
                }
            }
        }
    }

    fun clearNameError() {
        _stateFlow.value.let { state ->
            if (state is AuthViewState.Unauthorized.SignUp) {
                _stateFlow.value = state.copy(nameError = null)
            }
        }
    }

    fun clearEmailError() {
        _stateFlow.value.let { state ->
            if (state is AuthViewState.Unauthorized.Login) {
                _stateFlow.value = state.copy(emailError = null)
            }
            if (state is AuthViewState.Unauthorized.SignUp) {
                _stateFlow.value = state.copy(emailError = null)
            }
            if (state is AuthViewState.Unauthorized.ForgotPassword) {
                _stateFlow.value = state.copy(emailError = null)
            }
        }
    }

    fun clearPasswordError() {
        _stateFlow.value.let { state ->
            if (state is AuthViewState.Unauthorized.Login) {
                _stateFlow.value = state.copy(passwordError = null)
            }
            if (state is AuthViewState.Unauthorized.SignUp) {
                _stateFlow.value = state.copy(passwordError = null)
            }
        }
    }

    fun setLoading(isLoading: Boolean) {
        _stateFlow.value.let { state ->
            if (state is AuthViewState.Unauthorized.Login) {
                _stateFlow.value = state.copy(isLoading = isLoading)
            }
            if (state is AuthViewState.Unauthorized.SignUp) {
                _stateFlow.value = state.copy(isLoading = isLoading)
            }
        }
    }

    sealed interface AuthViewState {
        data class Loading(val progress: Float) : AuthViewState
        object Authorized : AuthViewState
        sealed class Unauthorized : AuthViewState {
            data class Login(
                val emailError: String? = null,
                val passwordError: String? = null,
                val isLoading: Boolean = false
            ) : Unauthorized()

            data class ForgotPassword(
                val emailError: String? = null,
                val isLoading: Boolean = false,
                val resetLinkSent: Boolean = false,
                val sentEmail: String? = null
            ) : Unauthorized()

            data class SignUp(
                val nameError: String? = null,
                val emailError: String? = null,
                val passwordError: String? = null,
                val isLoading: Boolean = false
            ) : Unauthorized()
        }

        object VerificationEmailSent : AuthViewState
        data class Error(val message: String? = null) : AuthViewState
    }
}

fun AuthState.toUi(): AuthViewState =
    when (this) {
        is AuthState.Authorized -> AuthViewState.Authorized
        is AuthState.Unauthorized -> AuthViewState.Unauthorized.Login()
        is AuthState.Loading -> AuthViewState.Loading(progress)
        is AuthState.Error -> AuthViewState.Error(message)
        is AuthState.VerificationEmailSent -> AuthViewState.VerificationEmailSent
        is AuthState.PasswordResetLinkSent -> AuthViewState.Unauthorized.ForgotPassword(
            resetLinkSent = true,
            sentEmail = email
        )
        else -> AuthViewState.Error()
    }