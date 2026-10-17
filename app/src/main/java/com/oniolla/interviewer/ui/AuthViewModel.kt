package com.oniolla.interviewer.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oniolla.interviewer.data.AuthState
import com.oniolla.interviewer.data.ApiException
import com.oniolla.interviewer.data.model.Specialization
import com.oniolla.interviewer.data.model.PreparationLanguage
import com.oniolla.interviewer.domain.ContentResult
import com.oniolla.interviewer.domain.GetPreparationLanguagesUseCase
import com.oniolla.interviewer.domain.SelectPreparationLanguageUseCase
import com.oniolla.interviewer.data.payload.LoginPayload
import com.oniolla.interviewer.data.payload.SignupPayload
import com.oniolla.interviewer.data.payload.VerifyEmailPayload
import com.oniolla.interviewer.data.toApiException
import com.oniolla.interviewer.data.toAuthState
import com.oniolla.interviewer.domain.ForgotPasswordUseCase
import com.oniolla.interviewer.domain.ClearSessionUseCase
import com.oniolla.interviewer.domain.ResendVerificationCodeUseCase
import com.oniolla.interviewer.domain.VerifyEmailUseCase
import com.oniolla.interviewer.domain.LoginUseCase
import com.oniolla.interviewer.domain.RestoreSessionUseCase
import com.oniolla.interviewer.domain.SignupUseCase
import com.oniolla.interviewer.domain.SelectSpecializationUseCase
import com.oniolla.interviewer.isNameValid
import com.oniolla.interviewer.isPasswordValid
import com.oniolla.interviewer.isValidEmail
import com.oniolla.interviewer.ui.AuthViewModel.AuthViewState
import dagger.hilt.android.lifecycle.HiltViewModel
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelChildren
import com.oniolla.interviewer.domain.ResetPasswordUseCase
import com.oniolla.interviewer.domain.PasswordResetResult
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val restoreSessionUseCase: RestoreSessionUseCase,
    private val loginUseCase: LoginUseCase,
    private val signupUseCase: SignupUseCase,
    private val forgotPasswordUseCase: ForgotPasswordUseCase,
    private val verifyEmailUseCase: VerifyEmailUseCase,
    private val resendVerificationCodeUseCase: ResendVerificationCodeUseCase,
    private val clearSessionUseCase: ClearSessionUseCase,
    private val selectSpecializationUseCase: SelectSpecializationUseCase,
    private val getPreparationLanguagesUseCase: GetPreparationLanguagesUseCase,
    private val selectPreparationLanguageUseCase: SelectPreparationLanguageUseCase,
    private val googleSignInUseCase: com.oniolla.interviewer.domain.GoogleSignInUseCase,
    private val resetPasswordUseCase: ResetPasswordUseCase
) : ViewModel() {

    private val _stateFlow: MutableStateFlow<AuthViewState> = MutableStateFlow(
        AuthViewState.Loading(
            0f
        )
    )
    val stateFlow = _stateFlow.asStateFlow()

    // Memory only: survives Activity recreation, never enters saved instance state.
    val passwordText = androidx.compose.runtime.mutableStateOf("")
    val resetConfirmationText = androidx.compose.runtime.mutableStateOf("")
    private var resetToken: String? = null

    private val _uiEventsFlow: MutableSharedFlow<UiEvent> = MutableSharedFlow()
    val uiEventsFlow = _uiEventsFlow.asSharedFlow()

    private var loadingJob: Job? = null
    private var signupDisposable: Disposable? = null
    private var preparationLanguagesJob: Job? = null
    private var googleOrigin: AuthViewState? = null
    private var googleExchangeJob: Job? = null

    override fun onCleared() {
        signupDisposable?.dispose()
        passwordText.value = ""
        resetConfirmationText.value = ""
        resetToken = null
        super.onCleared()
    }

    init {
        load()
    }

    fun openPasswordReset(token: String?) {
        // A new link supersedes startup restoration and any previous authentication operation.
        viewModelScope.coroutineContext.cancelChildren()
        signupDisposable?.dispose()
        googleOrigin = null
        passwordText.value = ""
        resetConfirmationText.value = ""
        resetToken = token
        _stateFlow.value = if (token == null) AuthViewState.PasswordResetFinished(invalidToken = true)
            else AuthViewState.PasswordReset()
    }

    fun clearResetPasswordErrors() {
        val state = _stateFlow.value as? AuthViewState.PasswordReset ?: return
        if (!state.isLoading) _stateFlow.value = state.copy(passwordError = null, confirmationError = null, error = null)
    }

    fun submitPasswordReset() {
        val state = _stateFlow.value as? AuthViewState.PasswordReset ?: return
        if (state.isLoading) return
        val token = resetToken ?: return
        val password = passwordText.value
        val passwordError = if (isPasswordValid(password)) null else "Password should be between 8 and 64 characters"
        val confirmationError = if (password == resetConfirmationText.value) null else "Passwords do not match"
        if (passwordError != null || confirmationError != null) {
            _stateFlow.value = state.copy(passwordError = passwordError, confirmationError = confirmationError, error = null)
            return
        }
        _stateFlow.value = state.copy(isLoading = true, passwordError = null, confirmationError = null, error = null)
        viewModelScope.launch {
            val result = resetPasswordUseCase(token, password)
            currentCoroutineContext().ensureActive()
            when (result) {
                PasswordResetResult.Success -> finishPasswordReset(invalidToken = false)
                PasswordResetResult.InvalidToken -> finishPasswordReset(invalidToken = true)
                is PasswordResetResult.Error -> _stateFlow.value = state.copy(error = result.message)
            }
        }
    }

    fun closePasswordReset() {
        val state = _stateFlow.value as? AuthViewState.PasswordReset ?: return
        if (!state.isLoading) finishPasswordReset(invalidToken = null)
    }

    private fun finishPasswordReset(invalidToken: Boolean?) {
        resetToken = null
        passwordText.value = ""
        resetConfirmationText.value = ""
        _stateFlow.value = AuthViewState.PasswordResetFinished(invalidToken)
    }

    fun leavePasswordReset() {
        if (_stateFlow.value !is AuthViewState.PasswordResetFinished) return
        _stateFlow.value = if (resetPasswordUseCase.hasSession()) AuthViewState.Authorized
            else AuthViewState.Unauthorized.Login()
    }

    fun load() {
        passwordText.value = ""
        loadingJob?.cancel()
        preparationLanguagesJob?.cancel()
        _stateFlow.value = AuthViewState.Loading(0f)
        loadingJob = viewModelScope.launch {
            restoreSessionUseCase(Unit).collect { result ->
                showAuthState(result)
                if (result is AuthState.EmailVerificationPending && result.message != null) {
                    _uiEventsFlow.emit(UiEvent.Toast(result.message))
                }
            }
        }
    }

    fun showLogin() {
        if (isBusy()) return
        passwordText.value = ""
        _stateFlow.value = AuthViewState.Unauthorized.Login()
    }

    fun showSignup() {
        if (isBusy()) return
        passwordText.value = ""
        _stateFlow.value = AuthViewState.Unauthorized.SignUp()
    }

    fun showForgotPassword() {
        if ((_stateFlow.value as? AuthViewState.Unauthorized.Login)?.isLoading == true) return
        passwordText.value = ""
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

    fun beginGoogleSignIn(): Boolean {
        if (isBusy()) return false
        val state = _stateFlow.value
        googleOrigin = when (state) {
            is AuthViewState.Unauthorized.Login -> state.copy(emailError = null, passwordError = null, googleError = null)
            is AuthViewState.Unauthorized.SignUp -> state.copy(nameError = null, emailError = null, passwordError = null, googleError = null)
            else -> return false
        }
        _stateFlow.value = when (val origin = googleOrigin) {
            is AuthViewState.Unauthorized.Login -> origin.copy(isLoading = true, isGoogleLoading = true)
            is AuthViewState.Unauthorized.SignUp -> origin.copy(isLoading = true, isGoogleLoading = true)
            else -> return false
        }
        return true
    }

    fun googleTokenReceived(idToken: String) {
        val origin = googleOrigin ?: return
        if (googleExchangeJob?.isActive == true) return
        if (idToken.isBlank()) {
            googleSignInFailed("Unable to sign in with Google. Please try again.")
            return
        }
        googleExchangeJob = viewModelScope.launch {
            when (val result = googleSignInUseCase(com.oniolla.interviewer.domain.GoogleSignInRequest(
                idToken, origin is AuthViewState.Unauthorized.SignUp))) {
                is AuthState.Error -> {
                    val message = result.message ?: "Unable to sign in with Google."
                    restoreGoogleForm(message)
                    _uiEventsFlow.emit(UiEvent.Toast(message))
                }
                else -> {
                    googleOrigin = null
                    showAuthState(result)
                }
            }
        }
    }

    fun googleSignInCancelled() {
        if (googleOrigin != null && googleExchangeJob?.isActive != true) restoreGoogleForm(null)
    }

    fun googleSignInFailed(message: String) {
        if (googleOrigin == null || googleExchangeJob?.isActive == true) return
        restoreGoogleForm(message)
        viewModelScope.launch { _uiEventsFlow.emit(UiEvent.Toast(message)) }
    }

    private fun restoreGoogleForm(error: String?) {
        val origin = googleOrigin ?: return
        googleOrigin = null
        _stateFlow.value = when (origin) {
            is AuthViewState.Unauthorized.Login -> origin.copy(isLoading = false, isGoogleLoading = false, googleError = error)
            is AuthViewState.Unauthorized.SignUp -> origin.copy(isLoading = false, isGoogleLoading = false, googleError = error)
            else -> origin
        }
    }

    fun signup(name: String, email: String, password: String) {
        val state = _stateFlow.value as? AuthViewState.Unauthorized.SignUp ?: return
        if (state.isLoading) return
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
                signupDisposable = signupUseCase(SignupPayload(email.trim(), password, name))
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                        {
                            setLoading(false)
                            viewModelScope.launch {
                                showAuthState(AuthState.VerificationEmailSent(email.trim()), verificationPassword = password)
                                _uiEventsFlow.emit(UiEvent.Toast(it.message))
                            }
                        },
                        {
                            setLoading(false)
                            val error = it.toApiException()
                            if (error is ApiException.BadRequest && error.customError.code == "EMAIL_ALREADY_IN_USE") {
                                val current = _stateFlow.value as? AuthViewState.Unauthorized.SignUp
                                if (current != null) _stateFlow.value = current.copy(
                                    emailError = error.message?.takeIf { message -> message.isNotBlank() }
                                        ?: "This email is already in use."
                                )
                            } else {
                                val authState = error.toAuthState()
                                if (authState is AuthState.Error) {
                                    viewModelScope.launch {
                                        _uiEventsFlow.emit(UiEvent.Toast(authState.message ?: "Unknown error"))
                                    }
                                }
                            }
                        })
            }
        }
    }

    fun login(email: String, password: String) {
        val state = _stateFlow.value as? AuthViewState.Unauthorized.Login ?: return
        if (state.isLoading) return
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
                val authState = loginUseCase(LoginPayload(email.trim(), password))
                authState.toUi().let { state ->
                    setLoading(false)
                    when (state) {
                        is AuthViewState.Error ->
                            _uiEventsFlow.emit(
                                UiEvent.Toast(
                                    state.message ?: "Unknown error"
                                )
                            )

                        else -> showAuthState(authState, verificationPassword = password)
                    }
                }
            }
        }
    }

    private fun isBusy(): Boolean = when (val state = _stateFlow.value) {
        is AuthViewState.PasswordReset -> state.isLoading
        is AuthViewState.Unauthorized.Login -> state.isLoading
        is AuthViewState.Unauthorized.SignUp -> state.isLoading
        is AuthViewState.Unauthorized.ForgotPassword -> state.isLoading
        is AuthViewState.Unauthorized.EmailConfirmation -> state.isLoading
        is AuthViewState.SpecializationSelection -> state.isLoading
        is AuthViewState.PreparationLanguageSelection -> state.isSaving
        else -> false
    }

    fun selectSpecialization(specialization: Specialization) {
        val state = _stateFlow.value as? AuthViewState.SpecializationSelection ?: return
        if (!state.isLoading) {
            _stateFlow.value = state.copy(selectedSpecialization = specialization, error = null)
        }
    }

    fun continueWithSpecialization() {
        val state = _stateFlow.value as? AuthViewState.SpecializationSelection ?: return
        if (state.isLoading) return
        _stateFlow.value = state.copy(isLoading = true, error = null)
        viewModelScope.launch {
            when (val result = selectSpecializationUseCase(state.selectedSpecialization)) {
                is AuthState.Error -> {
                    val message = result.message ?: "Unknown error"
                    _stateFlow.value = state.copy(error = message)
                    _uiEventsFlow.emit(UiEvent.Toast(message))
                }
                AuthState.SpecializationSelectionRequired -> {
                    val message = "Specialization was not saved"
                    _stateFlow.value = state.copy(error = message)
                    _uiEventsFlow.emit(UiEvent.Toast(message))
                }
                else -> showAuthState(result)
            }
        }
    }

    private fun showAuthState(result: AuthState, verificationPassword: String? = null) {
        preparationLanguagesJob?.cancel()
        val next = result.toUi()
        if (next is AuthViewState.Unauthorized.EmailConfirmation) {
            passwordText.value = verificationPassword.orEmpty()
            _stateFlow.value = next.copy(requiresPassword = verificationPassword.isNullOrEmpty())
        } else {
            passwordText.value = ""
            _stateFlow.value = next
        }
        if (result is AuthState.PreparationLanguageSelectionRequired) loadPreparationLanguages()
    }

    fun loadPreparationLanguages() {
        val state = _stateFlow.value as? AuthViewState.PreparationLanguageSelection ?: return
        if (state.isSaving || preparationLanguagesJob?.isActive == true) return
        _stateFlow.value = state.copy(isLoadingLanguages = true, languagesError = null, error = null)
        preparationLanguagesJob = viewModelScope.launch {
            val result = getPreparationLanguagesUseCase(Unit)
            currentCoroutineContext().ensureActive()
            val current = _stateFlow.value as? AuthViewState.PreparationLanguageSelection ?: return@launch
            when (result) {
                is ContentResult.Success -> {
                    val languages = result.content
                    val selected = languages.firstOrNull { it.code == current.selectedLanguage }
                        ?: languages.firstOrNull { it.code == "en" }
                        ?: languages.firstOrNull()
                    _stateFlow.value = current.copy(
                        languages = languages, selectedLanguage = selected?.code,
                        isLoadingLanguages = false, languagesError = null
                    )
                }
                is ContentResult.Error -> {
                    _stateFlow.value = current.copy(isLoadingLanguages = false, languagesError = result.message)
                    _uiEventsFlow.emit(UiEvent.Toast(result.message))
                }
                ContentResult.SessionExpired -> {
                    clearSessionUseCase(Unit)
                    showAuthState(AuthState.Unauthorized)
                }
            }
        }
    }

    fun selectPreparationLanguage(code: String) {
        val state = _stateFlow.value as? AuthViewState.PreparationLanguageSelection ?: return
        if (state.isSaving || state.isLoadingLanguages || state.languages.none { it.code == code }) return
        _stateFlow.value = state.copy(selectedLanguage = code, error = null)
    }

    fun backToSpecialization() {
        val state = _stateFlow.value as? AuthViewState.PreparationLanguageSelection ?: return
        if (state.isSaving) return
        preparationLanguagesJob?.cancel()
        _stateFlow.value = AuthViewState.SpecializationSelection(selectedSpecialization = state.specialization)
    }

    fun continueWithPreparationLanguage() {
        val state = _stateFlow.value as? AuthViewState.PreparationLanguageSelection ?: return
        if (state.isSaving || state.isLoadingLanguages || state.languagesError != null) return
        val language = state.languages.firstOrNull {
            it.code == state.selectedLanguage && it.contentAvailable
        } ?: return
        _stateFlow.value = state.copy(isSaving = true, error = null)
        viewModelScope.launch {
            when (val result = selectPreparationLanguageUseCase(language.code)) {
                is AuthState.Error -> {
                    val message = result.message ?: "Unknown error"
                    _stateFlow.value = state.copy(error = message)
                    _uiEventsFlow.emit(UiEvent.Toast(message))
                }
                else -> showAuthState(result)
            }
        }
    }

    fun changeEmailAddress() {
        if (isBusy()) return
        viewModelScope.launch {
            clearSessionUseCase(Unit)
            showLogin()
        }
    }

    fun updateEmailCode(value: String) {
        val state = _stateFlow.value as? AuthViewState.Unauthorized.EmailConfirmation ?: return
        if (!state.isLoading) {
            _stateFlow.value = state.copy(code = normalizeEmailCode(value), codeError = null)
        }
    }

    fun pasteEmailCode(value: String?) {
        if (isBusy()) return
        val code = normalizeEmailCode(value.orEmpty())
        if (isEmailCodeValid(code)) {
            updateEmailCode(code)
        } else {
            viewModelScope.launch {
                _uiEventsFlow.emit(UiEvent.Toast("No 6-digit code in clipboard"))
            }
        }
    }

    fun clearEmailCodeError() {
        val state = _stateFlow.value as? AuthViewState.Unauthorized.EmailConfirmation ?: return
        _stateFlow.value = state.copy(codeError = null)
    }

    fun confirmEmail() {
        val state = _stateFlow.value as? AuthViewState.Unauthorized.EmailConfirmation ?: return
        if (state.isLoading) return
        if (!isEmailCodeValid(state.code)) {
            _stateFlow.value = state.copy(codeError = "Enter the 6-digit code")
            return
        }
        val password = passwordText.value
        if (!isPasswordValid(password)) {
            _stateFlow.value = state.copy(requiresPassword = true,
                passwordError = "Password should be between 8 and 64 characters")
            return
        }
        _stateFlow.value = state.copy(isLoading = true, isResending = false, codeError = null, passwordError = null)
        viewModelScope.launch {
            when (val result = verifyEmailUseCase(VerifyEmailPayload(state.email, state.code, password))) {
                is AuthState.EmailVerificationFailed -> {
                    val message = result.message ?: "Unknown error"
                    _stateFlow.value = state.copy(codeError = message)
                    _uiEventsFlow.emit(UiEvent.Toast(message))
                }
                else -> showAuthState(result)
            }
        }
    }

    fun resendEmailCode() {
        val state = _stateFlow.value as? AuthViewState.Unauthorized.EmailConfirmation ?: return
        if (state.isLoading) return
        _stateFlow.value = state.copy(isLoading = true, isResending = true, codeError = null)
        viewModelScope.launch {
            when (val result = resendVerificationCodeUseCase(state.email)) {
                is AuthState.VerificationEmailSent -> {
                    _stateFlow.value = state.copy(code = "", codeError = null, isLoading = false, isResending = false)
                    result.message?.let { _uiEventsFlow.emit(UiEvent.Toast(it)) }
                }
                is AuthState.Error -> {
                    _stateFlow.value = state.copy(codeError = null)
                    _uiEventsFlow.emit(UiEvent.Toast(result.message ?: "Unknown error"))
                }
                else -> {
                    _stateFlow.value = state.copy(codeError = null)
                    _uiEventsFlow.emit(UiEvent.Toast("Unable to send verification code"))
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
            if (state is AuthViewState.Unauthorized.EmailConfirmation) {
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
        data class PasswordReset(
            val passwordError: String? = null,
            val confirmationError: String? = null,
            val error: String? = null,
            val isLoading: Boolean = false
        ) : AuthViewState
        data class PasswordResetFinished(val invalidToken: Boolean?) : AuthViewState
        data class Loading(val progress: Float) : AuthViewState
        object Authorized : AuthViewState
        sealed class Unauthorized : AuthViewState {
            data class Login(
                val emailError: String? = null,
                val passwordError: String? = null,
                val isLoading: Boolean = false,
                val isGoogleLoading: Boolean = false,
                val googleError: String? = null
            ) : Unauthorized()

            data class EmailConfirmation(
                val email: String,
                val code: String = "",
                val codeError: String? = null,
                val isLoading: Boolean = false,
                val isResending: Boolean = false,
                val requiresPassword: Boolean = false,
                val passwordError: String? = null
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
                val isLoading: Boolean = false,
                val isGoogleLoading: Boolean = false,
                val googleError: String? = null
            ) : Unauthorized()
        }

        data class SpecializationSelection(
            val selectedSpecialization: Specialization = Specialization.ANDROID,
            val isLoading: Boolean = false,
            val error: String? = null
        ) : AuthViewState

        data class PreparationLanguageSelection(
            val specialization: Specialization,
            val languages: List<PreparationLanguage> = emptyList(),
            val selectedLanguage: String? = null,
            val isLoadingLanguages: Boolean = true,
            val isSaving: Boolean = false,
            val languagesError: String? = null,
            val error: String? = null
        ) : AuthViewState

        object OnboardingRequired : AuthViewState
        data class Error(val message: String? = null) : AuthViewState
    }
}

fun AuthState.toUi(): AuthViewState =
    when (this) {
        is AuthState.Authorized -> AuthViewState.Authorized
        is AuthState.Unauthorized -> AuthViewState.Unauthorized.Login()
        is AuthState.Loading -> AuthViewState.Loading(progress)
        is AuthState.Error -> AuthViewState.Error(message)
        is AuthState.VerificationEmailSent -> AuthViewState.Unauthorized.EmailConfirmation(email)
        is AuthState.EmailVerificationPending -> AuthViewState.Unauthorized.EmailConfirmation(email, codeError = message)
        is AuthState.EmailVerificationFailed -> AuthViewState.Error(message)
        is AuthState.SpecializationSelectionRequired -> AuthViewState.SpecializationSelection()
        is AuthState.PreparationLanguageSelectionRequired -> AuthViewState.PreparationLanguageSelection(
            specialization = specialization, selectedLanguage = currentLanguage
        )
        is AuthState.OnboardingRequired -> AuthViewState.OnboardingRequired
        is AuthState.PasswordResetLinkSent -> AuthViewState.Unauthorized.ForgotPassword(
            resetLinkSent = true,
            sentEmail = email
        )
        else -> AuthViewState.Error()
    }