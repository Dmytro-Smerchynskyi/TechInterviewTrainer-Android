package com.oniolla.interviewer

import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.oniolla.interviewer.data.model.Specialization
import com.oniolla.interviewer.ui.AuthViewModel
import com.oniolla.interviewer.ui.UiEvent
import com.oniolla.interviewer.ui.EMAIL_CODE_LENGTH
import com.oniolla.interviewer.ui.normalizeEmailCode
import com.oniolla.interviewer.ui.theme.AppTheme
import com.oniolla.interviewer.ui.theme.InterviewTrainerTheme
import com.oniolla.interviewer.ui.theme.StatusBarTheme
import com.oniolla.interviewer.ui.theme.applyStatusBarTheme
import com.oniolla.interviewer.ui.theme.fonts
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class AuthActivity : ComponentActivity() {

    @javax.inject.Inject lateinit var preferences: com.oniolla.interviewer.data.AppPreferences
    private val viewModel: AuthViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyStatusBarTheme(preferences.theme.value)
        setContent {
            val theme by preferences.theme.collectAsStateWithLifecycle()
            StatusBarTheme(this@AuthActivity, theme)
            InterviewTrainerTheme(themeMode = theme) {
                Screen(viewModel, onAuthorized = ::openHome)
            }
        }
    }

    private fun openHome() {
        if (!isFinishing) {
            startActivity(Intent(this, HomeActivity::class.java))
            finish()
        }
    }
}

@Composable
fun Screen(viewModel: AuthViewModel, onAuthorized: () -> Unit = {}) {
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.uiEventsFlow.collect { event ->
            when (event) {
                is UiEvent.Toast -> Toast.makeText(
                    context,
                    event.message,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    val state = viewModel.stateFlow.collectAsStateWithLifecycle()

    LaunchedEffect(state.value) {
        if (state.value is AuthViewModel.AuthViewState.Authorized) onAuthorized()
    }

    BackHandler(enabled = state.value is AuthViewModel.AuthViewState.SpecializationSelection) { }
    BackHandler(enabled = state.value is AuthViewModel.AuthViewState.PreparationLanguageSelection) {
        viewModel.backToSpecialization()
    }

    val nameText = rememberSaveable { mutableStateOf("") }
    val emailText = rememberSaveable { mutableStateOf("") }
    val passwordText = rememberSaveable { mutableStateOf("") }

    BackHandler(
        enabled = state.value is AuthViewModel.AuthViewState.Unauthorized.ForgotPassword ||
                state.value is AuthViewModel.AuthViewState.Unauthorized.EmailConfirmation
    ) {
        if (state.value is AuthViewModel.AuthViewState.Unauthorized.EmailConfirmation) {
            viewModel.changeEmailAddress()
        } else {
            viewModel.showLogin()
        }
    }

    AnimatedContent(
        modifier = Modifier.fillMaxSize().background(AppTheme.colors.background),
        targetState = state.value,
        contentKey = { state ->
            state::class
        },
        transitionSpec = {
            when (initialState) {
                is AuthViewModel.AuthViewState.Unauthorized.Login if (targetState is AuthViewModel.AuthViewState.Unauthorized.SignUp || targetState is AuthViewModel.AuthViewState.Unauthorized.ForgotPassword) -> {

                    slideInHorizontally { it } togetherWith
                            slideOutHorizontally { -it }
                }

                is AuthViewModel.AuthViewState.Unauthorized.SignUp if targetState is AuthViewModel.AuthViewState.Unauthorized.Login -> {

                    slideInHorizontally { -it } togetherWith
                            slideOutHorizontally { it }
                }

                is AuthViewModel.AuthViewState.Unauthorized.ForgotPassword if targetState is AuthViewModel.AuthViewState.Unauthorized.Login -> {
                    slideInHorizontally { -it } togetherWith
                            slideOutHorizontally { it }
                }

                is AuthViewModel.AuthViewState.Unauthorized.EmailConfirmation if targetState is AuthViewModel.AuthViewState.Unauthorized.Login -> {
                    slideInHorizontally { -it } togetherWith slideOutHorizontally { it }
                }

                is AuthViewModel.AuthViewState.Unauthorized if targetState is AuthViewModel.AuthViewState.Unauthorized.EmailConfirmation -> {
                    slideInHorizontally { it } togetherWith slideOutHorizontally { -it }
                }

                is AuthViewModel.AuthViewState.Unauthorized if targetState is AuthViewModel.AuthViewState.SpecializationSelection -> {
                    slideInHorizontally { it } togetherWith slideOutHorizontally { -it }
                }

                is AuthViewModel.AuthViewState.SpecializationSelection if targetState is AuthViewModel.AuthViewState.PreparationLanguageSelection -> {
                    slideInHorizontally { it } togetherWith slideOutHorizontally { -it }
                }

                is AuthViewModel.AuthViewState.PreparationLanguageSelection if targetState is AuthViewModel.AuthViewState.SpecializationSelection -> {
                    slideInHorizontally { -it } togetherWith slideOutHorizontally { it }
                }

                is AuthViewModel.AuthViewState.Unauthorized if targetState is AuthViewModel.AuthViewState.PreparationLanguageSelection -> {
                    slideInHorizontally { it } togetherWith slideOutHorizontally { -it }
                }

                is AuthViewModel.AuthViewState.Loading -> {
                    fadeIn() togetherWith fadeOut()
                }

                else -> {
                    EnterTransition.None togetherWith ExitTransition.None
                }
            }
        },
        label = "AuthState"
    ) { state ->
        state.let { state ->
            when (state) {
                is AuthViewModel.AuthViewState.Loading ->
                    Loading(
                        state.progress
                    )

                is AuthViewModel.AuthViewState.Unauthorized.SignUp ->
                    SignUp(
                        isLoading = state.isLoading,
                        nameText = nameText,
                        emailText = emailText,
                        passwordText = passwordText,
                        nameError = state.nameError,
                        emailError = state.emailError,
                        passwordError = state.passwordError,
                        onSignInClicked = viewModel::showLogin,
                        onSignUpRequested = viewModel::signup,
                        onNameFocused = viewModel::clearNameError,
                        onEmailFocused = viewModel::clearEmailError,
                        onPasswordFocused = viewModel::clearPasswordError
                    )

                is AuthViewModel.AuthViewState.Unauthorized.Login ->
                    Login(
                        isLoading = state.isLoading,
                        emailText = emailText,
                        passwordText = passwordText,
                        emailError = state.emailError,
                        passwordError = state.passwordError,
                        onSignUpClicked = viewModel::showSignup,
                        onForgotPasswordClicked = viewModel::showForgotPassword,
                        onLoginRequested = viewModel::login,
                        onEmailFocused = viewModel::clearEmailError,
                        onPasswordFocused = viewModel::clearPasswordError
                    )

                is AuthViewModel.AuthViewState.Unauthorized.ForgotPassword ->
                    PasswordRecovery(
                        isLoading = state.isLoading,
                        emailText = emailText,
                        emailError = state.emailError,
                        resetLinkSent = state.resetLinkSent,
                        onBackClicked = viewModel::showLogin,
                        onResetRequested = viewModel::forgotPassword,
                        onSendAgainClicked = viewModel::resendPasswordResetLink,
                        onEmailFocused = viewModel::clearEmailError,
                        onEmailChanged = viewModel::onRecoveryEmailChanged
                    )

                is AuthViewModel.AuthViewState.Unauthorized.EmailConfirmation ->
                    EmailConfirmation(
                        email = state.email,
                        code = state.code,
                        codeError = state.codeError,
                        isLoading = state.isLoading,
                        isResending = state.isResending,
                        onCodeChanged = viewModel::updateEmailCode,
                        onCodeFocused = viewModel::clearEmailCodeError,
                        onConfirmClicked = viewModel::confirmEmail,
                        onResendClicked = viewModel::resendEmailCode,
                        onChangeEmailClicked = viewModel::changeEmailAddress,
                        onPasteRequested = viewModel::pasteEmailCode
                    )

                is AuthViewModel.AuthViewState.SpecializationSelection ->
                    SpecializationSelection(
                        selectedSpecialization = state.selectedSpecialization,
                        isLoading = state.isLoading,
                        error = state.error,
                        onSpecializationSelected = viewModel::selectSpecialization,
                        onContinueClicked = viewModel::continueWithSpecialization
                    )

                is AuthViewModel.AuthViewState.PreparationLanguageSelection ->
                    PreparationLanguageSelection(
                        state = state,
                        onBack = viewModel::backToSpecialization,
                        onLanguageSelected = viewModel::selectPreparationLanguage,
                        onContinue = viewModel::continueWithPreparationLanguage,
                        onRetry = viewModel::loadPreparationLanguages
                    )

                is AuthViewModel.AuthViewState.Authorized ->
                    Box(Modifier.fillMaxSize().background(AppTheme.colors.background))

                is AuthViewModel.AuthViewState.Error ->
                    AuthError(state.message, viewModel::load)

                else -> {
                    Scaffold {
                        Box(
                            modifier = Modifier
                                .padding(it)
                                .fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                when (state) {
                                    is AuthViewModel.AuthViewState.Error -> "Error"
                                    is AuthViewModel.AuthViewState.Authorized -> "Authorized"
                                    is AuthViewModel.AuthViewState.OnboardingRequired -> "Onboarding required"
                                    else -> "Unknown state"
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun Loading(loadingProgress: Float) {
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(AppTheme.colors.background)
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .offset(y = (-50).dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .background(
                        color = AppTheme.colors.splashBackground1,
                        shape = CircleShape
                    )
                    .size(196.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .background(
                            color = AppTheme.colors.splashBackground2,
                            shape = CircleShape
                        )
                        .size(132.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .background(
                                brush = Brush.horizontalGradient(
                                    Pair(0f, AppTheme.colors.leftGradient),
                                    Pair(1f, AppTheme.colors.rightGradient)
                                ),
                                shape = RoundedCornerShape(40.dp)
                            )
                            .size(92.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        QuestionIcon(27.dp)
                    }
                }
            }

            Column(
                modifier = Modifier
                    .offset(y = (100).dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Title(27.sp)

                Text(
                    text = stringResource(R.string.app_slogan),
                    color = AppTheme.colors.secondaryText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal
                )
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom
            ) {
                GradientLinearProgress(
                    modifier = Modifier
                        .width(84.dp)
                        .height(4.dp),
                    trackColor = AppTheme.colors.progressBackground,
                    brush = Brush.horizontalGradient(
                        Pair(0f, AppTheme.colors.leftGradient),
                        Pair(1f, AppTheme.colors.rightGradient)
                    )
                ) {
                    loadingProgress
                }

                Text(
                    text = stringResource(R.string.preparing_your_workspace),
                    color = AppTheme.colors.secondaryText,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Normal
                )
            }
        }
    }
}

@Composable
fun SignUp(
    isLoading: Boolean = false,
    nameText: MutableState<String> = mutableStateOf(""),
    emailText: MutableState<String> = mutableStateOf(""),
    passwordText: MutableState<String> = mutableStateOf(""),
    nameError: String? = null,
    emailError: String? = null,
    passwordError: String? = null,
    onSignInClicked: () -> Unit = {},
    onSignUpRequested: (String, String, String) -> Unit = { _, _, _ -> },
    onNameFocused: () -> Unit = {},
    onEmailFocused: () -> Unit = {},
    onPasswordFocused: () -> Unit = {}
) {
    val scrollState = rememberScrollState()
//    val density = LocalDensity.current
//
//    val imeHeight = WindowInsets.ime.getBottom(density)
//    val isImeVisible = imeHeight > 0
//
//    LaunchedEffect(isImeVisible, scrollState.maxValue) {
//        if (isImeVisible) {
//            scrollState.animateScrollTo(scrollState.maxValue)
//        }
//    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .background(AppTheme.colors.background)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(innerPadding)
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(50.dp))

            AuthHeader()

            Spacer(modifier = Modifier.height(27.dp))

            Text(
                text = stringResource(R.string.create_your_account),
                color = AppTheme.colors.primaryText,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = stringResource(R.string.create_your_account_subtext),
                color = AppTheme.colors.secondaryText,
                fontSize = 10.sp,
                fontWeight = FontWeight.Normal
            )

            AppTextField(
                modifier = Modifier
                    .padding(top = 12.dp),
                text = nameText,
                header = stringResource(R.string.name),
                placeholder = stringResource(R.string.name_placeholder),
                icon = ImageVector.vectorResource(id = R.drawable.user),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Next
                ),
                error = nameError,
                onFocused = onNameFocused,
                enabled = !isLoading
            )

            AppTextField(
                modifier = Modifier
                    .padding(top = 6.dp),
                text = emailText,
                header = stringResource(R.string.email),
                placeholder = stringResource(R.string.email_placeholder),
                icon = ImageVector.vectorResource(id = R.drawable.mail),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next
                ),
                error = emailError,
                onFocused = onEmailFocused,
                enabled = !isLoading
            )

            AppTextField(
                modifier = Modifier
                    .padding(top = 6.dp),
                text = passwordText,
                header = stringResource(R.string.password),
                placeholder = stringResource(R.string.password_placeholder),
                icon = ImageVector.vectorResource(id = R.drawable.lock),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                passwordVisible = false,
                error = passwordError,
                onFocused = onPasswordFocused,
                onDone = { onSignUpRequested(nameText.value, emailText.value, passwordText.value) },
                enabled = !isLoading
            )

            MainButton(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 18.dp),
                buttonText = stringResource(R.string.create_account),
                loadingButtonText = stringResource(R.string.creating_account),
                onClick = {
                    onSignUpRequested(nameText.value, emailText.value, passwordText.value)
                },
                isLoading = isLoading
            )

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = stringResource(R.string.already_have_an_account),
                    color = AppTheme.colors.secondaryText,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Normal
                )

                Spacer(modifier = Modifier.width(8.dp))

                LinkText(
                    text = stringResource(R.string.sign_in),
                    enabled = !isLoading,
                    onClick = onSignInClicked
                )
            }
        }
    }
}

@Composable
fun Login(
    isLoading: Boolean = false,
    emailText: MutableState<String> = mutableStateOf(""),
    passwordText: MutableState<String> = mutableStateOf(""),
    emailError: String? = null,
    passwordError: String? = null,
    onSignUpClicked: () -> Unit = {},
    onForgotPasswordClicked: () -> Unit = {},
    onLoginRequested: (String, String) -> Unit = { _, _ -> },
    onEmailFocused: () -> Unit = {},
    onPasswordFocused: () -> Unit = {},
) {
    val scrollState = rememberScrollState()

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .background(AppTheme.colors.background)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(innerPadding)
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(50.dp))

            AuthHeader()

            Spacer(modifier = Modifier.height(27.dp))

            Text(
                text = stringResource(R.string.welcome_back),
                color = AppTheme.colors.primaryText,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = stringResource(R.string.welcome_back_subtext),
                color = AppTheme.colors.secondaryText,
                fontSize = 10.sp,
                fontWeight = FontWeight.Normal
            )

            AppTextField(
                modifier = Modifier
                    .padding(top = 12.dp),
                text = emailText,
                header = stringResource(R.string.email),
                placeholder = stringResource(R.string.email_placeholder),
                icon = ImageVector.vectorResource(id = R.drawable.mail),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next
                ),
                error = emailError,
                onFocused = onEmailFocused,
                enabled = !isLoading
            )

            AppTextField(
                modifier = Modifier
                    .padding(top = 6.dp),
                text = passwordText,
                header = stringResource(R.string.password),
                placeholder = stringResource(R.string.password_placeholder),
                icon = ImageVector.vectorResource(id = R.drawable.lock),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                passwordVisible = false,
                error = passwordError,
                onFocused = onPasswordFocused,
                onDone = { onLoginRequested(emailText.value, passwordText.value) },
                enabled = !isLoading
            )

            LinkText(
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(top = 4.dp),
                text = stringResource(R.string.forgot_password),
                enabled = !isLoading,
                onClick = onForgotPasswordClicked
            )

            MainButton(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 18.dp),
                buttonText = stringResource(R.string.sign_in),
                onClick = {
                    onLoginRequested(emailText.value, passwordText.value)
                },
                isLoading = isLoading,
                loadingButtonText = stringResource(R.string.signing_in)
            )

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = stringResource(R.string.do_not_have_an_account),
                    color = AppTheme.colors.secondaryText,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Normal
                )

                Spacer(modifier = Modifier.width(8.dp))

                LinkText(
                    text = stringResource(R.string.create_account),
                    enabled = !isLoading,
                    onClick = onSignUpClicked,
                )
            }
        }
    }
}

@Composable
fun PasswordRecovery(
    isLoading: Boolean = false,
    emailText: MutableState<String> = mutableStateOf(""),
    emailError: String? = null,
    resetLinkSent: Boolean = false,
    onBackClicked: () -> Unit = {},
    onResetRequested: (String) -> Unit = {},
    onSendAgainClicked: () -> Unit = {},
    onEmailFocused: () -> Unit = {},
    onEmailChanged: () -> Unit = {}
) {
    Scaffold(
        modifier = Modifier.fillMaxSize().imePadding(),
        containerColor = AppTheme.colors.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            TextButton(
                modifier = Modifier.padding(top = 10.dp).size(36.dp),
                onClick = onBackClicked,
                enabled = !isLoading,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
            ) {
                Text(
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Start,
                    text = "‹",
                    color = AppTheme.colors.secondaryText,
                    fontSize = 28.sp,
                    lineHeight = 36.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(Modifier.height(36.dp))

            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(80.dp)
                    .background(AppTheme.colors.link.copy(alpha = 0.11f), CircleShape)
                    .border(1.dp, AppTheme.colors.stroke, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = ImageVector.vectorResource(R.drawable.recovery_mail),
                    contentDescription = null,
                    tint = AppTheme.colors.link,
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(Modifier.height(34.dp))

            Text(
                modifier = Modifier.fillMaxWidth(),
                text = stringResource(R.string.reset_your_password),
                color = AppTheme.colors.primaryText,
                textAlign = TextAlign.Center,
                fontSize = 23.sp,
                lineHeight = 29.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(11.dp))

            Text(
                modifier = Modifier.fillMaxWidth(),
                text = stringResource(R.string.reset_password_subtext),
                color = AppTheme.colors.secondaryText,
                textAlign = TextAlign.Center,
                fontSize = 10.sp,
                lineHeight = 15.sp
            )

            Spacer(Modifier.height(40.dp))

            AppTextField(
                modifier = Modifier,
                text = emailText,
                header = stringResource(R.string.email),
                icon = ImageVector.vectorResource(R.drawable.mail),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Done
                ),
                error = emailError,
                onFocused = onEmailFocused,
                onDone = { onResetRequested(emailText.value) },
                onValueChanged = onEmailChanged,
                placeholder = stringResource(R.string.email_placeholder),
                fieldHeight = 50.dp,
                headerSpacing = 7.dp,
                headerLineHeight = 13.sp,
                enabled = !isLoading
            )

            MainButton(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                buttonText = stringResource(R.string.send_reset_link),
                loadingButtonText = stringResource(R.string.sending_reset_link),
                onClick = { onResetRequested(emailText.value) },
                isLoading = isLoading,
                textSize = 12.sp
            )

            AnimatedVisibility(
                visible = resetLinkSent,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .padding(top = 34.dp)
                            .fillMaxWidth()
                            .heightIn(min = 112.dp)
                            .background(AppTheme.colors.success.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
                            .padding(start = 16.dp, end = 28.dp, top = 16.dp, bottom = 16.dp)
                    ) {
                        Icon(
                            imageVector = ImageVector.vectorResource(R.drawable.recovery_check),
                            contentDescription = null,
                            tint = AppTheme.colors.success,
                            modifier = Modifier.padding(top = 2.dp).size(24.dp)
                        )
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.reset_link_sent),
                                color = AppTheme.colors.success,
                                fontSize = 13.sp,
                                lineHeight = 17.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = stringResource(R.string.reset_link_sent_subtext),
                                color = AppTheme.colors.secondaryText,
                                fontSize = 9.sp,
                                lineHeight = 14.sp
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 34.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.did_not_receive_reset_link),
                            color = AppTheme.colors.secondaryText,
                            fontSize = 10.sp,
                            lineHeight = 13.sp
                        )
                        Spacer(Modifier.width(12.dp))
                        LinkText(
                            text = stringResource(R.string.send_again),
                            enabled = !isLoading,
                            onClick = onSendAgainClicked,
                            lineHeight = 13.sp
                        )
                    }
                }
            }

            Spacer(Modifier.height(40.dp))
        }
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
fun PasswordRecoveryLightPreview() {
    InterviewTrainerTheme(darkTheme = false) {
        PasswordRecovery(resetLinkSent = true)
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
fun PasswordRecoveryDarkPreview() {
    InterviewTrainerTheme(darkTheme = true) {
        PasswordRecovery(resetLinkSent = true)
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
fun PasswordRecoveryLoadingPreview() {
    InterviewTrainerTheme(darkTheme = true) {
        PasswordRecovery(isLoading = true)
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
fun PasswordRecoveryErrorPreview() {
    InterviewTrainerTheme(darkTheme = false) {
        PasswordRecovery(emailError = "Invalid email format")
    }
}

@Composable
fun EmailConfirmation(
    email: String,
    code: String = "",
    codeError: String? = null,
    isLoading: Boolean = false,
    isResending: Boolean = false,
    onCodeChanged: (String) -> Unit = {},
    onCodeFocused: () -> Unit = {},
    onConfirmClicked: () -> Unit = {},
    onResendClicked: () -> Unit = {},
    onChangeEmailClicked: () -> Unit = {},
    onPasteRequested: (String?) -> Unit = {}
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var codeFocused by remember { mutableStateOf(false) }
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0

    Scaffold(
        modifier = Modifier.fillMaxSize().imePadding(),
        containerColor = AppTheme.colors.background,
        bottomBar = {
            AnimatedVisibility(
                visible = imeVisible && codeFocused && !isLoading,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(AppTheme.colors.container)
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    LinkText(
                        text = stringResource(R.string.paste_email_code),
                        onClick = {
                            val clipboard = context.getSystemService(ClipboardManager::class.java)
                            val text = clipboard?.primaryClip?.takeIf { it.itemCount > 0 }
                                ?.getItemAt(0)?.coerceToText(context)?.toString()
                            onPasteRequested(text)
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(30.dp))
            AuthHeader(iconSize = 32.dp, iconResource = R.drawable.email_confirmation_questions)
            Spacer(Modifier.height(35.dp))

            Text(
                text = stringResource(R.string.confirm_your_email),
                color = AppTheme.colors.primaryText,
                textAlign = TextAlign.Center,
                fontSize = 22.sp,
                lineHeight = 28.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.email_confirmation_subtext),
                color = AppTheme.colors.secondaryText,
                textAlign = TextAlign.Center,
                fontSize = 10.sp,
                lineHeight = 13.sp
            )
            Spacer(Modifier.height(45.dp))

            EmailCodeField(
                email = email,
                code = code,
                error = codeError,
                enabled = !isLoading,
                onCodeChanged = onCodeChanged,
                onFocused = {
                    codeFocused = it
                    if (it) onCodeFocused()
                },
                onDone = {
                    focusManager.clearFocus()
                    keyboardController?.hide()
                    onConfirmClicked()
                }
            )

            Spacer(Modifier.height(14.dp))
            Text(
                text = stringResource(R.string.email_code_expiry),
                color = AppTheme.colors.secondaryText,
                fontSize = 10.sp,
                lineHeight = 13.sp
            )

            MainButton(
                modifier = Modifier.fillMaxWidth().padding(top = 27.dp),
                buttonText = stringResource(R.string.confirm_email),
                loadingButtonText = stringResource(
                    if (isResending) R.string.sending_verification_code else R.string.confirming_email
                ),
                isLoading = isLoading,
                textSize = 12.sp,
                onClick = onConfirmClicked
            )

            Row(
                modifier = Modifier.padding(top = 26.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = stringResource(R.string.did_not_get_email_code),
                    color = AppTheme.colors.secondaryText,
                    fontSize = 10.sp,
                    lineHeight = 13.sp
                )
                Spacer(Modifier.width(8.dp))
                LinkText(
                    text = stringResource(R.string.resend_email_code),
                    enabled = !isLoading,
                    lineHeight = 13.sp,
                    onClick = {
                        focusManager.clearFocus()
                        keyboardController?.hide()
                        onResendClicked()
                    }
                )
            }

            LinkText(
                modifier = Modifier.padding(top = 23.dp),
                text = stringResource(R.string.change_email_address),
                enabled = !isLoading,
                lineHeight = 13.sp,
                onClick = {
                    focusManager.clearFocus()
                    keyboardController?.hide()
                    onChangeEmailClicked()
                }
            )
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
fun EmailCodeField(
    email: String,
    code: String,
    error: String?,
    enabled: Boolean,
    onCodeChanged: (String) -> Unit,
    onFocused: (Boolean) -> Unit,
    onDone: () -> Unit
) {
    var value by rememberSaveable(email, stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(code, TextRange(code.length)))
    }
    var focused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val label = stringResource(R.string.six_digit_email_code)

    LaunchedEffect(code) {
        if (value.text != code) value = TextFieldValue(code, TextRange(code.length))
    }

    val containerColor by animateColorAsState(
        if (enabled) AppTheme.colors.container else AppTheme.colors.containerDisabled,
        animationSpec = tween(300)
    )
    val textColor by animateColorAsState(
        if (error != null) AppTheme.colors.red
        else if (enabled) AppTheme.colors.secondaryText
        else AppTheme.colors.secondaryDisabledText,
        animationSpec = tween(300)
    )

    Column(Modifier.fillMaxWidth()) {
        Text(
            text = label,
            color = AppTheme.colors.secondaryText,
            fontSize = 10.sp,
            lineHeight = 13.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.height(13.dp))

        BasicTextField(
            value = value,
            onValueChange = { newValue ->
                val text = normalizeEmailCode(newValue.text)
                val selectionStart = normalizeEmailCode(newValue.text.take(newValue.selection.start)).length
                val selectionEnd = normalizeEmailCode(newValue.text.take(newValue.selection.end)).length
                value = TextFieldValue(text, TextRange(selectionStart, selectionEnd))
                onCodeChanged(text)
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .focusRequester(focusRequester)
                .onFocusChanged {
                    focused = it.isFocused
                    onFocused(it.isFocused)
                }
                .semantics { contentDescription = label },
            enabled = enabled,
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = { onDone() }),
            textStyle = TextStyle(fontFamily = fonts, fontSize = 20.sp),
            cursorBrush = SolidColor(Color.Transparent),
            decorationBox = { innerTextField ->
                Box(Modifier.fillMaxSize()) {
                    Box(Modifier.matchParentSize().alpha(0f)) {
                        innerTextField()
                    }
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        repeat(EMAIL_CODE_LENGTH) { index ->
                            val active = focused && value.selection.start.coerceAtMost(EMAIL_CODE_LENGTH - 1) == index
                            val borderColor by animateColorAsState(
                                if (active) AppTheme.colors.accent
                                else if (error != null) AppTheme.colors.red
                                else if (enabled) AppTheme.colors.stroke
                                else AppTheme.colors.strokeDisabled,
                                animationSpec = tween(300)
                            )
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(56.dp)
                                    .background(containerColor, RoundedCornerShape(14.dp))
                                    .border(1.dp, borderColor, RoundedCornerShape(14.dp))
                                    .clickable(
                                        enabled = enabled,
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) {
                                        focusRequester.requestFocus()
                                        val start = index.coerceAtMost(value.text.length)
                                        val end = (index + 1).coerceAtMost(value.text.length)
                                        value = value.copy(selection = TextRange(start, end))
                                        keyboardController?.show()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = value.text.getOrNull(index)?.toString().orEmpty(),
                                    color = textColor,
                                    fontSize = 20.sp,
                                    lineHeight = 24.sp,
                                    fontWeight = FontWeight.Normal
                                )
                            }
                        }
                    }
                }
            }
        )
        AnimatedVisibility(
            visible = error != null,
            enter = fadeIn(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Text(
                text = error.orEmpty(),
                color = AppTheme.colors.red,
                fontSize = 10.sp
            )
        }
    }
}

@Composable
fun AuthError(message: String?, onRetry: () -> Unit) {
    Scaffold(containerColor = AppTheme.colors.background) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = message ?: stringResource(R.string.session_loading_failed),
                color = AppTheme.colors.secondaryText,
                textAlign = TextAlign.Center
            )
            MainButton(
                modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
                buttonText = stringResource(R.string.retry),
                onClick = onRetry
            )
        }
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
fun EmailConfirmationLightPreview() {
    InterviewTrainerTheme(darkTheme = false) {
        EmailConfirmation(email = "developer@example.com", code = "123456")
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
fun EmailConfirmationDarkPreview() {
    InterviewTrainerTheme(darkTheme = true) {
        EmailConfirmation(email = "developer@example.com", code = "123456")
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
fun EmailConfirmationErrorPreview() {
    InterviewTrainerTheme(darkTheme = false) {
        EmailConfirmation(email = "developer@example.com", code = "123456", codeError = "Invalid verification code")
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
fun EmailConfirmationLoadingPreview() {
    InterviewTrainerTheme(darkTheme = true) {
        EmailConfirmation(email = "developer@example.com", code = "123456", isLoading = true)
    }
}

@Composable
fun SpecializationSelection(
    selectedSpecialization: Specialization = Specialization.ANDROID,
    isLoading: Boolean = false,
    error: String? = null,
    onSpecializationSelected: (Specialization) -> Unit = {},
    onContinueClicked: () -> Unit = {},
    onBack: (() -> Unit)? = null
) {
    Scaffold(containerColor = AppTheme.colors.background) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onBack != null) {
                    Surface(onClick = onBack, enabled = !isLoading,
                        modifier = Modifier.size(24.dp), shape = CircleShape,
                        color = androidx.compose.ui.graphics.Color.Transparent) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("‹", color = AppTheme.colors.secondaryText, fontSize = 28.sp)
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text = stringResource(R.string.specialization),
                    color = AppTheme.colors.primaryText,
                    fontSize = 18.sp, lineHeight = 23.sp, fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(27.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.horizontalGradient(listOf(AppTheme.colors.heroStart, AppTheme.colors.heroEnd)),
                        RoundedCornerShape(20.dp)
                    )
                    .border(1.dp, AppTheme.colors.stroke, RoundedCornerShape(20.dp))
                    .padding(start = 18.dp, end = 18.dp, top = 20.dp, bottom = 10.dp)
            ) {
                Icon(
                    imageVector = ImageVector.vectorResource(R.drawable.specialization_grade),
                    contentDescription = null,
                    tint = AppTheme.colors.info,
                    modifier = Modifier.size(34.dp)
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.choose_your_specialization),
                    color = AppTheme.colors.primaryText,
                    fontSize = 20.sp,
                    lineHeight = 25.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(7.dp))
                Text(
                    text = stringResource(R.string.specialization_subtext),
                    color = AppTheme.colors.secondaryText,
                    fontSize = 10.sp,
                    lineHeight = 14.sp
                )
            }

            Spacer(Modifier.height(18.dp))
            Text(
                text = stringResource(R.string.choose_one_specialization),
                color = AppTheme.colors.primaryText,
                fontSize = 14.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(10.dp))

            Column(
                modifier = Modifier.fillMaxWidth().selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Specialization.entries.chunked(2).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        row.forEach { specialization ->
                            SpecializationOption(
                                modifier = Modifier.weight(1f),
                                specialization = specialization,
                                selected = selectedSpecialization == specialization,
                                enabled = !isLoading,
                                onClick = { onSpecializationSelected(specialization) }
                            )
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .padding(top = 16.dp)
                    .fillMaxWidth()
                    .heightIn(min = 42.dp)
                    .background(AppTheme.colors.info.copy(alpha = 0.07f), RoundedCornerShape(13.dp))
                    .padding(horizontal = 12.dp, vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.specialization_can_be_changed),
                    color = AppTheme.colors.secondaryText,
                    fontSize = 10.sp,
                    lineHeight = 13.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )
            }

            AnimatedVisibility(
                visible = error != null,
                enter = fadeIn(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Text(
                    modifier = Modifier.padding(top = 8.dp),
                    text = error.orEmpty(),
                    color = AppTheme.colors.red,
                    fontSize = 10.sp
                )
            }

            MainButton(
                modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                buttonText = stringResource(R.string.continue_onboarding),
                loadingButtonText = stringResource(R.string.saving_specialization),
                isLoading = isLoading,
                textSize = 12.sp,
                onClick = onContinueClicked
            )
            Spacer(Modifier.height(16.dp))
            Text(
                modifier = Modifier.fillMaxWidth(),
                text = stringResource(R.string.specialization_personalizes_app),
                color = AppTheme.colors.primaryText,
                fontSize = 9.sp,
                lineHeight = 12.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
fun SpecializationOption(
    modifier: Modifier = Modifier,
    specialization: Specialization,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    OnboardingChoiceOption(
        modifier = modifier,
        badge = specialization.badge,
        title = stringResource(specialization.titleResource),
        description = stringResource(specialization.descriptionResource),
        selected = selected,
        enabled = enabled,
        onClick = onClick
    )
}

internal val Specialization.badge: String
    get() = when (this) {
        Specialization.ANDROID -> "AD"
        Specialization.IOS -> "iOS"
        Specialization.DOTNET -> ".NET"
        Specialization.FRONTEND -> "FE"
        Specialization.BACKEND -> "BE"
        Specialization.QA -> "QA"
        Specialization.DATA_ML -> "ML"
        Specialization.DEVOPS -> "DO"
    }

internal val Specialization.titleResource: Int
    get() = when (this) {
        Specialization.ANDROID -> R.string.specialization_android
        Specialization.IOS -> R.string.specialization_ios
        Specialization.DOTNET -> R.string.specialization_dotnet
        Specialization.FRONTEND -> R.string.specialization_frontend
        Specialization.BACKEND -> R.string.specialization_backend
        Specialization.QA -> R.string.specialization_qa
        Specialization.DATA_ML -> R.string.specialization_data_ml
        Specialization.DEVOPS -> R.string.specialization_devops
    }

private val Specialization.descriptionResource: Int
    get() = when (this) {
        Specialization.ANDROID -> R.string.specialization_android_description
        Specialization.IOS -> R.string.specialization_ios_description
        Specialization.DOTNET -> R.string.specialization_dotnet_description
        Specialization.FRONTEND -> R.string.specialization_frontend_description
        Specialization.BACKEND -> R.string.specialization_backend_description
        Specialization.QA -> R.string.specialization_qa_description
        Specialization.DATA_ML -> R.string.specialization_data_ml_description
        Specialization.DEVOPS -> R.string.specialization_devops_description
    }

@Preview(widthDp = 390, heightDp = 844)
@Composable
fun SpecializationSelectionLightPreview() {
    InterviewTrainerTheme(darkTheme = false) { SpecializationSelection() }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
fun SpecializationSelectionDarkPreview() {
    InterviewTrainerTheme(darkTheme = true) { SpecializationSelection() }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
fun SpecializationSelectionLoadingPreview() {
    InterviewTrainerTheme(darkTheme = true) {
        SpecializationSelection(selectedSpecialization = Specialization.DATA_ML, isLoading = true)
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
fun SpecializationSelectionErrorPreview() {
    InterviewTrainerTheme(darkTheme = false) {
        SpecializationSelection(selectedSpecialization = Specialization.IOS, error = "Unable to save specialization")
    }
}

@Composable
fun LinkText(
    modifier: Modifier = Modifier,
    text: String = "",
    enabled: Boolean = true,
    onClick: () -> Unit = {},
    lineHeight: TextUnit = TextUnit.Unspecified
) {
    val interactionSource = remember { MutableInteractionSource() }

    Text(
        modifier = modifier
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled
            ) { onClick() },
        text = text,
        color = AppTheme.colors.link,
        lineHeight = lineHeight,
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
fun AppTextField(
    modifier: Modifier,
    text: MutableState<String>,
    header: String,
    icon: ImageVector,
    keyboardOptions: KeyboardOptions,
    passwordVisible: Boolean = true,
    error: String? = null,
    onFocused: () -> Unit = {},
    onDone: () -> Unit = {},
    enabled: Boolean = true,
    placeholder: String? = null,
    onValueChanged: () -> Unit = {},
    fieldHeight: Dp? = null,
    headerSpacing: Dp = 0.dp,
    headerLineHeight: TextUnit = TextUnit.Unspecified
) {
//    val bringIntoViewRequester = remember {
//        BringIntoViewRequester()
//    }
//
//    val scope = rememberCoroutineScope()

    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    var focused by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
    ) {
        Text(
            text = header,
            lineHeight = headerLineHeight,
            color = AppTheme.colors.secondaryText,
            fontSize = 10.sp,
            fontWeight = FontWeight.Normal
        )

        val borderColor by animateColorAsState(
            targetValue =
                if (focused) AppTheme.colors.accent
                else if (error != null) AppTheme.colors.red
                else if (enabled) AppTheme.colors.stroke
                else AppTheme.colors.strokeDisabled,
            animationSpec = tween(300)
        )

        val containerColor by animateColorAsState(
            targetValue = if (enabled) AppTheme.colors.container else AppTheme.colors.containerDisabled,
            animationSpec = tween(300)
        )

        val iconColor by animateColorAsState(
            targetValue =
                if (focused) AppTheme.colors.accent
                else if (error != null) AppTheme.colors.red
                else if (enabled) AppTheme.colors.secondaryText
                else AppTheme.colors.secondaryDisabledText,
            animationSpec = tween(300)
        )

        val textColor by animateColorAsState(
            targetValue = if (error != null) AppTheme.colors.red
            else if (enabled) AppTheme.colors.secondaryText
            else AppTheme.colors.secondaryDisabledText,
            animationSpec = tween(300)
        )

        TextField(
            value = text.value,
            onValueChange = {
                text.value = it
                onValueChanged()
            },
            placeholder = {
                placeholder?.let { hint ->
                    Text(text = hint, style = TextStyle(fontFamily = fonts, fontSize = 11.sp),
                        color = AppTheme.colors.secondaryText.copy(alpha = 0.5f))
                }
            },
            modifier = Modifier
                .padding(top = headerSpacing)
                .then(if (fieldHeight != null) Modifier.height(fieldHeight) else Modifier)
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    color = borderColor,
                    shape = RoundedCornerShape(14.dp)
                )
//                .bringIntoViewRequester(bringIntoViewRequester)
//                .onFocusChanged { focusState ->
//                    if (focusState.isFocused) {
//                        scope.launch {
//                            bringIntoViewRequester.bringIntoView()
//                        }
//                    }
//                }
                .onFocusChanged {
                    focused = it.isFocused
                    if (it.isFocused) {
                        onFocused()
                    }
                },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = containerColor,
                unfocusedContainerColor = containerColor,
                disabledContainerColor = containerColor,
                errorContainerColor = containerColor,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
                cursorColor = AppTheme.colors.secondaryText,
            ),
            leadingIcon = {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor
                )
            },
            textStyle = TextStyle(
                color = textColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Normal,
                fontFamily = fonts
            ),
            keyboardOptions = keyboardOptions,
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            isError = error != null,
            keyboardActions = KeyboardActions(
                onDone = {
                    focusManager.clearFocus()
                    keyboardController?.hide()

                    onDone()
                }
            ),
            enabled = enabled
        )

        AnimatedVisibility(
            visible = error != null,
            enter = fadeIn(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Text(
                text = error.orEmpty(),
                color = AppTheme.colors.red,
                fontSize = 10.sp,
                fontWeight = FontWeight.Normal
            )
        }
    }
}

@Composable
fun AuthHeader(
    modifier: Modifier = Modifier,
    iconSize: Dp = 24.dp,
    iconResource: Int = R.drawable.questions
) {
    Column(
        modifier = modifier
            .fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .background(
                    Brush.horizontalGradient(
                        Pair(0f, AppTheme.colors.leftGradient),
                        Pair(1f, AppTheme.colors.rightGradient)
                    ),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            QuestionIcon(iconSize, iconResource)
        }

        Spacer(modifier = Modifier.height(20.dp))

        Title(24.sp)

        Text(
            stringResource(R.string.sign_up_slogan),
            color = AppTheme.colors.secondaryText,
            fontSize = 10.sp,
            fontWeight = FontWeight.Normal
        )
    }
}

@Composable
fun QuestionIcon(size: Dp, iconResource: Int = R.drawable.questions) {
    Icon(
        imageVector = ImageVector.vectorResource(id = iconResource),
        contentDescription = null,
        tint = Color.White,
        modifier = Modifier.size(size)
    )
}

@Composable
fun Title(textSize: TextUnit) {
    Text(
        text = stringResource(R.string.app_name),
        color = AppTheme.colors.primaryText,
        fontSize = textSize,
        fontWeight = FontWeight.Bold
    )
}

@Preview
@Composable
fun LoginLightPreview() {
    InterviewTrainerTheme(darkTheme = false) {
        Login(
            isLoading = true,
            emailError = "Wrong email format"
        )
    }
}

@Preview
@Composable
fun LoginDarkPreview() {
    InterviewTrainerTheme(darkTheme = true) {
        Login()
    }
}

@Preview
@Composable
fun SignUpLightPreview() {
    InterviewTrainerTheme(darkTheme = false) {
        SignUp()
    }
}

@Preview
@Composable
fun SignUpDarkPreview() {
    InterviewTrainerTheme(darkTheme = true) {
        SignUp()
    }
}

@Preview
@Composable
fun ScreenLightPreview() {
    InterviewTrainerTheme(darkTheme = false) {
        Loading(0.5f)
    }
}

@Preview
@Composable
fun ScreenDarkPreview() {
    InterviewTrainerTheme(darkTheme = true) {
        Loading(0.3f)
    }
}