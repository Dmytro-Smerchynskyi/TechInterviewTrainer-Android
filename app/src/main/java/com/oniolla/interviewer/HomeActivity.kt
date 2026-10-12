package com.oniolla.interviewer

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.oniolla.interviewer.ui.UiEvent
import com.oniolla.interviewer.ui.home.HomeScreen
import com.oniolla.interviewer.ui.home.HomeTab
import com.oniolla.interviewer.ui.home.HomeViewModel
import com.oniolla.interviewer.ui.theme.InterviewTrainerTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class HomeActivity : ComponentActivity() {
    private val viewModel: HomeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            InterviewTrainerTheme {
                val state by viewModel.stateFlow.collectAsStateWithLifecycle()
                LaunchedEffect(Unit) {
                    lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                        viewModel.uiEventsFlow.collect { event ->
                            if (event is UiEvent.Toast) Toast.makeText(
                                this@HomeActivity, event.message, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                LaunchedEffect(state.sessionExpired) {
                    if (state.sessionExpired && !isFinishing) {
                        startActivity(Intent(this@HomeActivity, AuthActivity::class.java))
                        finish()
                    }
                }
                BackHandler(enabled = state.tab != HomeTab.HOME) { viewModel.selectTab(HomeTab.HOME) }
                HomeScreen(
                    state = state,
                    onTabSelected = viewModel::selectTab,
                    onTopicQuestions = viewModel::openTopicQuestions,
                    onQuestionSearch = viewModel::updateQuestionSearch,
                    onRetryDashboard = viewModel::loadDashboard,
                    onRetryTopics = viewModel::loadTopics,
                    onQuestionTopicSelected = viewModel::selectQuestionTopic,
                    onClearQuestionTopic = viewModel::clearQuestionTopic,
                    onRetryQuestions = viewModel::loadQuestions,
                    onLoadMoreQuestions = viewModel::loadMoreQuestions,
                    onRetryQuestionPage = viewModel::retryQuestionPage
                )
            }
        }
    }
}
