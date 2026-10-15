package com.oniolla.interviewer.ui.home

import android.os.SystemClock
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oniolla.interviewer.LinkText
import com.oniolla.interviewer.MainButton
import com.oniolla.interviewer.R
import com.oniolla.interviewer.data.model.PracticeGrade
import com.oniolla.interviewer.ui.theme.AppTheme
import com.oniolla.interviewer.ui.theme.fonts
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
fun PracticeScreens(state: PracticeViewState, onBack: () -> Unit = {}, onClose: () -> Unit = {},
    onGrade: (PracticeGrade) -> Unit = {}, onContinue: () -> Unit = {}, onMode: (AnswerMode) -> Unit = {},
    onAnswer: (String) -> Unit = {}, onEvaluate: () -> Unit = {}, onPlans: () -> Unit = {},
    onRecordingStarted: (Long) -> Unit = {}, onTranscribing: () -> Unit = {},
    onRecordingFinished: (String?) -> Unit = {}, onVoiceLevel: (Float) -> Unit = {}) {
    val visiblePage = if (state.page == PracticePage.CLOSED) state.previousPage else state.page
    PracticeTextTheme {
    AnimatedContent(visiblePage, modifier = Modifier.fillMaxSize().background(AppTheme.colors.background),
        transitionSpec = { fadeIn(tween(250)).togetherWith(fadeOut(tween(250))).using(null) },
        contentAlignment = Alignment.TopCenter, label = "practice navigation") { page ->
        when (page) {
            PracticePage.GRADE -> PracticeGradeScreen(state, onBack, onGrade, onContinue)
            PracticePage.ANSWERING -> PracticeAnswerScreen(state, onBack, onMode, onAnswer, onEvaluate,
                onRecordingStarted, onTranscribing, onRecordingFinished, onVoiceLevel)
            PracticePage.EVALUATING -> PracticeEvaluatingScreen(state)
            PracticePage.REVIEW -> PracticeReviewScreen(state, onClose)
            PracticePage.LIMIT -> PracticeLimitScreen(state, onClose, onPlans)
            PracticePage.CLOSED -> Unit
        }
    }
    }
}

@Composable
private fun PracticeGradeScreen(state: PracticeViewState, onBack: () -> Unit,
    onGrade: (PracticeGrade) -> Unit, onContinue: () -> Unit) {
    Scaffold(containerColor = AppTheme.colors.background,
        topBar = { PracticeHeader(stringResource(R.string.practice_title), onBack) },
        bottomBar = {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 20.dp, end = 20.dp,
                top = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Surface(shape = RoundedCornerShape(13.dp), color = AppTheme.colors.info.copy(alpha = 0.07f)) {
                    Box(Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(18.dp), contentAlignment = Alignment.CenterStart) {
                        Text(state.reviewsRemaining?.let { stringResource(R.string.practice_usage, it) }
                            ?: stringResource(R.string.practice_usage_unknown), fontSize = 10.sp, lineHeight = 13.sp,
                            color = AppTheme.colors.secondaryText)
                    }
                }
                MainButton(Modifier.fillMaxWidth().height(50.dp), onClick = onContinue,
                    buttonText = stringResource(R.string.practice_continue), textSize = 12.sp)
            }
        }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)
            .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(8.dp))
            PracticeCard(Modifier.fillMaxWidth().heightIn(min = 158.dp), hero = true, corner = 20.dp,
                contentPadding = PaddingValues(18.dp), borderColor = AppTheme.colors.stroke) {
                PracticeIcon(R.drawable.specialization_grade, 34.dp)
                Spacer(Modifier.height(14.dp))
                Text(stringResource(R.string.practice_choose_level), fontSize = 20.sp, lineHeight = 26.sp,
                    fontWeight = FontWeight.Bold, color = AppTheme.colors.primaryText)
                Spacer(Modifier.height(7.dp))
                Text(stringResource(R.string.practice_grade_explanation), fontSize = 10.sp, lineHeight = 14.sp,
                    color = AppTheme.colors.secondaryText)
            }
            Text(stringResource(R.string.practice_select_grade), color = AppTheme.colors.primaryText,
                fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 28.dp, bottom = 16.dp))
            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                PracticeGrade.entries.forEach { grade -> GradeChoice(grade, grade == state.grade) { onGrade(grade) } }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun GradeChoice(grade: PracticeGrade, selected: Boolean, onClick: () -> Unit) {
    val color = when (grade) {
        PracticeGrade.JUNIOR -> AppTheme.colors.success
        PracticeGrade.MIDDLE -> AppTheme.colors.leftGradient
        PracticeGrade.SENIOR -> AppTheme.colors.link
    }
    val background by animateColorAsState(
        if (selected) AppTheme.colors.leftGradient.copy(alpha = 0.1f) else AppTheme.colors.container,
        tween(200), label = "grade background")
    val border by animateColorAsState(
        if (selected) AppTheme.colors.leftGradient else AppTheme.colors.stroke,
        tween(200), label = "grade border")
    Surface(selected = selected, onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 108.dp).semantics { role = Role.RadioButton },
        shape = RoundedCornerShape(16.dp),
        color = background, border = BorderStroke(1.dp, border)) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Top) {
            Box(Modifier.size(44.dp).background(color.copy(alpha = 0.14f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center) { PracticeIcon(R.drawable.practice_grade, 24.dp, color) }
            Column(Modifier.weight(1f)) {
                Row(Modifier.heightIn(min = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(gradeLabel(grade.name), Modifier.weight(1f), color = AppTheme.colors.primaryText,
                        fontSize = 15.sp, lineHeight = 19.sp, fontWeight = FontWeight.SemiBold)
                    if (selected) Surface(shape = RoundedCornerShape(12.dp),
                        color = AppTheme.colors.leftGradient.copy(alpha = 0.15f)) {
                        Row(Modifier.heightIn(min = 24.dp).padding(horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            PracticeIcon(R.drawable.specialization_check, 14.dp, AppTheme.colors.leftGradient)
                            Text(stringResource(R.string.practice_selected), fontSize = 8.sp, lineHeight = 10.sp,
                                color = AppTheme.colors.leftGradient)
                        }
                    }
                }
                Spacer(Modifier.height(7.dp))
                Text(stringResource(when (grade) {
                    PracticeGrade.JUNIOR -> R.string.practice_junior_description
                    PracticeGrade.MIDDLE -> R.string.practice_middle_description
                    PracticeGrade.SENIOR -> R.string.practice_senior_description
                }), color = AppTheme.colors.secondaryText, fontSize = 9.sp, lineHeight = 13.sp)
            }
        }
    }
}

@Composable
private fun PracticeAnswerScreen(state: PracticeViewState, onBack: () -> Unit, onMode: (AnswerMode) -> Unit,
    onAnswer: (String) -> Unit, onEvaluate: () -> Unit, onStarted: (Long) -> Unit,
    onTranscribing: () -> Unit, onFinished: (String?) -> Unit, onLevel: (Float) -> Unit) {
    val voice = rememberPracticeVoiceInput(state, onAnswer, onStarted, onTranscribing, onFinished, onLevel)
    Scaffold(Modifier.imePadding(), containerColor = AppTheme.colors.background,
        topBar = { PracticeHeader(stringResource(R.string.practice_answer_title), { voice.cancel(); onBack() }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)
            .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(20.dp))
            state.question?.let { PracticeQuestionCard(it) }
            Spacer(Modifier.height(18.dp))
            AnswerModeSelector(state.mode) { voice.cancel(); onMode(it) }
            Spacer(Modifier.height(16.dp))
            if (state.mode == AnswerMode.VOICE) VoiceAnswerPanel(state, voice) { voice.cancel(); onMode(AnswerMode.TEXT) }
            else TypedAnswerPanel(state.answer, onAnswer)
            AnimatedVisibility(state.answerError != null, enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()) {
                Text(state.answerError.orEmpty(), Modifier.padding(top = 12.dp), color = AppTheme.colors.red,
                    fontSize = 11.sp, lineHeight = 15.sp)
            }
            Spacer(Modifier.height(18.dp))
            MainButton(Modifier.fillMaxWidth().height(48.dp).alpha(if (state.canEvaluate) 1f else 0.4f),
                buttonText = stringResource(R.string.practice_evaluate), textSize = 11.sp,
                leadingIcon = { PracticeIcon(R.drawable.practice_ai, 22.dp, Color.White) },
                enabled = state.canEvaluate, onClick = onEvaluate)
            Spacer(Modifier.height(16.dp))
            PracticeInfo(text = stringResource(R.string.practice_ai_note))
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun AnswerModeSelector(mode: AnswerMode, onMode: (AnswerMode) -> Unit) {
    Surface(shape = RoundedCornerShape(15.dp), color = AppTheme.colors.container,
        border = BorderStroke(1.dp, AppTheme.colors.stroke)) {
        Row(Modifier.fillMaxWidth().height(42.dp).padding(4.dp).selectableGroup()) {
            AnswerMode.entries.forEach { option ->
                val selected = mode == option
                val color = if (selected) AppTheme.colors.link else AppTheme.colors.secondaryText
                Surface(selected = selected, onClick = { onMode(option) }, modifier = Modifier.weight(1f).fillMaxHeight(),
                    shape = RoundedCornerShape(12.dp), color = if (selected) AppTheme.colors.link.copy(alpha = 0.13f)
                        else Color.Transparent) {
                    Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        PracticeIcon(if (option == AnswerMode.VOICE) R.drawable.practice_mic else R.drawable.practice_keyboard, 20.dp, color)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(if (option == AnswerMode.VOICE) R.string.practice_voice_answer else R.string.practice_type_answer),
                            color = color, fontSize = 10.sp, lineHeight = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun TypedAnswerPanel(answer: String, onAnswer: (String) -> Unit) {
    PracticeCard(Modifier.fillMaxWidth().heightIn(min = 278.dp)) {
        Text(stringResource(R.string.practice_your_answer), color = AppTheme.colors.primaryText,
            fontSize = 12.sp, lineHeight = 17.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(18.dp))
        val description = stringResource(R.string.practice_your_answer)
        BasicTextField(value = answer, onValueChange = onAnswer,
            modifier = Modifier.fillMaxWidth().heightIn(min = 182.dp)
                .background(if (AppTheme.colors.isDark) Color(0xFF101F37) else Color(0xFFEDF2F7),
                    RoundedCornerShape(12.dp)).padding(12.dp).semantics { contentDescription = description },
            textStyle = TextStyle(color = AppTheme.colors.primaryText, fontFamily = fonts,
                fontSize = 10.sp, lineHeight = 15.sp, platformStyle = PlatformTextStyle(includeFontPadding = false)),
            cursorBrush = SolidColor(AppTheme.colors.accent),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            decorationBox = { field ->
                Column {
                    Text(stringResource(R.string.practice_type_placeholder), color = AppTheme.colors.secondaryText,
                        fontSize = 9.sp, lineHeight = 12.sp)
                    Spacer(Modifier.height(10.dp))
                    field()
                }
            })
        Text(stringResource(R.string.practice_answer_count, answer.length), Modifier.align(Alignment.End).padding(top = 10.dp),
            color = AppTheme.colors.primaryText, fontSize = 8.sp, lineHeight = 11.sp)
    }
}

@Composable
private fun VoiceAnswerPanel(state: PracticeViewState, voice: VoiceInputActions, onEdit: () -> Unit) {
    var elapsed by remember { mutableLongStateOf(0L) }
    LaunchedEffect(state.isRecording, state.recordingStartedAt) {
        if (state.isRecording) while (true) {
            elapsed = ((SystemClock.elapsedRealtime() - (state.recordingStartedAt ?: SystemClock.elapsedRealtime())) / 1000).coerceAtLeast(0)
            delay(250)
        }
    }
    PracticeCard(Modifier.fillMaxWidth().heightIn(min = 278.dp), contentPadding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.practice_your_answer), Modifier.weight(1f), color = AppTheme.colors.primaryText,
                fontSize = 13.sp, lineHeight = 17.sp, fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.practice_voice_arrow), color = AppTheme.colors.secondaryText,
                fontSize = 9.sp, lineHeight = 12.sp)
        }
        Box(Modifier.fillMaxWidth().height(116.dp), contentAlignment = Alignment.TopCenter) {
            Image(painterResource(if (AppTheme.colors.isDark) R.drawable.practice_voice_glow_dark
                else R.drawable.practice_voice_glow_light), null, Modifier.size(116.dp))
            val description = stringResource(if (state.isRecording) R.string.practice_stop_recording else R.string.practice_record)
            Surface(onClick = voice.record, enabled = !state.isTranscribing, modifier = Modifier.padding(top = 22.dp)
                .size(68.dp).semantics { contentDescription = description }, shape = CircleShape, color = Color.Transparent) {
                Box(Modifier.background(Brush.horizontalGradient(listOf(AppTheme.colors.leftGradient, AppTheme.colors.rightGradient))),
                    contentAlignment = Alignment.Center) {
                    PracticeIcon(R.drawable.practice_record_mic, 32.dp, Color.White)
                }
            }
            Text(when {
                state.isTranscribing -> stringResource(R.string.practice_transcribing)
                state.isRecording -> stringResource(R.string.practice_recording, String.format(Locale.ROOT, "%02d:%02d", elapsed / 60, elapsed % 60))
                state.answer.isNotBlank() -> stringResource(R.string.practice_recorded)
                else -> stringResource(R.string.practice_tap_to_record)
            }, Modifier.align(Alignment.BottomCenter).padding(bottom = 3.dp),
                color = if (state.isRecording) AppTheme.colors.red else AppTheme.colors.secondaryText,
                fontSize = 10.sp, lineHeight = 13.sp, fontWeight = FontWeight.SemiBold)
        }
        VoiceWaveform(state.isRecording, state.voiceLevel)
        Spacer(Modifier.height(8.dp))
        Surface(shape = RoundedCornerShape(12.dp),
            color = if (AppTheme.colors.isDark) Color(0xFF101F37) else Color(0xFFEDF2F7)) {
            Column(Modifier.fillMaxWidth().heightIn(min = 68.dp, max = 160.dp).verticalScroll(rememberScrollState()).padding(12.dp)) {
                Text(stringResource(R.string.practice_transcript), color = AppTheme.colors.secondaryText, fontSize = 9.sp, lineHeight = 12.sp)
                Spacer(Modifier.height(4.dp))
                Text(state.answer.ifBlank { stringResource(R.string.practice_transcript_placeholder) },
                    color = if (state.answer.isBlank()) AppTheme.colors.secondaryText else AppTheme.colors.primaryText,
                    fontSize = 10.sp, lineHeight = 14.sp)
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            LinkText(text = stringResource(R.string.practice_rerecord), enabled = !state.isTranscribing && state.answer.isNotBlank(),
                onClick = voice.rerecord, lineHeight = 12.sp)
            LinkText(text = stringResource(R.string.practice_edit_transcript), enabled = state.answer.isNotBlank(), onClick = onEdit, lineHeight = 12.sp)
        }
        state.voiceError?.let { Text(it, Modifier.padding(top = 10.dp), color = AppTheme.colors.red, fontSize = 10.sp, lineHeight = 14.sp) }
    }
}

@Composable
private fun VoiceWaveform(recording: Boolean, level: Float) {
    val animation = rememberInfiniteTransition(label = "voice waveform")
    val pulse by animation.animateFloat(0.65f, 1f, infiniteRepeatable(tween(500), RepeatMode.Reverse), label = "wave pulse")
    Row(Modifier.fillMaxWidth().height(30.dp), horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically) {
        listOf(8,15,22,29,14,21,28,13,20,27,12).forEachIndexed { index, height ->
            if (index > 0) Spacer(Modifier.width(11.dp))
            Box(Modifier.width(4.dp).height((height * if (recording) pulse * (0.6f + level * 0.4f) else 0.45f).dp)
                .background((if (index % 3 == 0) AppTheme.colors.info else AppTheme.colors.link).copy(alpha = 0.85f),
                    RoundedCornerShape(2.dp)))
        }
    }
}

@Composable
private fun PracticeEvaluatingScreen(state: PracticeViewState) {
    var step by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { delay(1000); step = 1; delay(1000); step = 2 }
    Scaffold(containerColor = AppTheme.colors.background,
        topBar = { PracticeHeader(stringResource(R.string.practice_evaluating_title), {}, enabled = false) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)
            .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(20.dp))
            state.question?.let { PracticeQuestionCard(it) }
            Spacer(Modifier.height(42.dp))
            PracticeCard(Modifier.fillMaxWidth().heightIn(min = 250.dp), hero = true) {
                Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(Modifier.size(72.dp), color = AppTheme.colors.info,
                        strokeWidth = 5.dp, trackColor = AppTheme.colors.stroke)
                    Spacer(Modifier.height(16.dp))
                    Text(stringResource(R.string.practice_evaluating), color = AppTheme.colors.primaryText,
                        fontSize = 18.sp, lineHeight = 23.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(10.dp))
                    Text(stringResource(R.string.practice_evaluating_description), Modifier.widthIn(max = 286.dp), color = AppTheme.colors.primaryText,
                        fontSize = 9.sp, lineHeight = 13.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(20.dp))
                    AnimatedContent(step, transitionSpec = {
                        fadeIn(tween(1000)).togetherWith(fadeOut(tween(1000))).using(null)
                    }, label = "evaluation step") { index ->
                        Text(stringResource(when (index) {
                            0 -> R.string.practice_step_understanding
                            1 -> R.string.practice_step_comparing
                            else -> R.string.practice_step_feedback
                        }), color = AppTheme.colors.secondaryText, fontSize = 10.sp, lineHeight = 14.sp,
                            textAlign = TextAlign.Center)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
