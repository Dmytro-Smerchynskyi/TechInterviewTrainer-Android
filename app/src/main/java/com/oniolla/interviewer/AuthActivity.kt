package com.oniolla.interviewer

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
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
import com.oniolla.interviewer.ui.AuthViewModel
import com.oniolla.interviewer.ui.UiEvent
import com.oniolla.interviewer.ui.theme.AppTheme
import com.oniolla.interviewer.ui.theme.InterviewTrainerTheme
import com.oniolla.interviewer.ui.theme.fonts
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class AuthActivity : ComponentActivity() {

    private val viewModel: AuthViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            InterviewTrainerTheme {
                Screen(viewModel)
            }
        }
    }
}

@Composable
fun Screen(viewModel: AuthViewModel) {
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

    val nameText = rememberSaveable { mutableStateOf("") }
    val emailText = rememberSaveable { mutableStateOf("") }
    val passwordText = rememberSaveable { mutableStateOf("") }

    AnimatedContent(
        targetState = state.value,
        contentKey = { state ->
            state::class
        },
        transitionSpec = {
            when (initialState) {
                is AuthViewModel.AuthViewState.Unauthorized.Login if targetState is AuthViewModel.AuthViewState.Unauthorized.SignUp -> {

                    slideInHorizontally { it } togetherWith
                            slideOutHorizontally { -it }
                }

                is AuthViewModel.AuthViewState.Unauthorized.SignUp if targetState is AuthViewModel.AuthViewState.Unauthorized.Login -> {

                    slideInHorizontally { -it } togetherWith
                            slideOutHorizontally { it }
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
                        onLoginRequested = viewModel::login,
                        onEmailFocused = viewModel::clearEmailError,
                        onPasswordFocused = viewModel::clearPasswordError
                    )

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
                                    is AuthViewModel.AuthViewState.VerificationEmailSent -> "Email sent"
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
                onClick = { }
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
fun LinkText(
    modifier: Modifier = Modifier,
    text: String = "",
    enabled: Boolean = true,
    onClick: () -> Unit = {}
) {
    val interactionSource = remember { MutableInteractionSource() }

    Text(
        modifier = modifier
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) { if (!enabled) return@clickable else onClick() },
        text = text,
        color = AppTheme.colors.link,
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
fun MainButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit = { },
    buttonText: String = "",
    loadingButtonText: String = buttonText,
    isLoading: Boolean = false
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    Button(
        modifier = modifier
            .height(50.dp)
            .background(
                brush = Brush.horizontalGradient(
                    Pair(0f, AppTheme.colors.leftGradient),
                    Pair(1f, AppTheme.colors.rightGradient)
                ),
                RoundedCornerShape(50.dp)
            ),
        onClick = {
            focusManager.clearFocus()
            keyboardController?.hide()
            onClick()
        },
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent
        ),
        enabled = !isLoading
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AnimatedVisibility(
                visible = isLoading,
                enter = fadeIn() + expandHorizontally(),
                exit = fadeOut() + shrinkHorizontally()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )

                    Spacer(modifier = Modifier.width(6.dp))
                }
            }

            Text(
                text = if (isLoading) loadingButtonText else buttonText,
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

    }
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
    enabled: Boolean = true
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
            onValueChange = { text.value = it },
            modifier = Modifier
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
            exit = fadeOut()
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
fun AuthHeader(modifier: Modifier = Modifier) {
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
            QuestionIcon(24.dp)
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
fun QuestionIcon(size: Dp) {
    Icon(
        imageVector = ImageVector.vectorResource(id = R.drawable.questions),
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