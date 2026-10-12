package com.oniolla.interviewer.ui.home

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oniolla.interviewer.domain.ContentResult
import com.oniolla.interviewer.domain.GetDashboardUseCase
import com.oniolla.interviewer.domain.GetTopicsUseCase
import com.oniolla.interviewer.ui.UiEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val getDashboardUseCase: GetDashboardUseCase,
    private val getTopicsUseCase: GetTopicsUseCase,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val _stateFlow = MutableStateFlow(HomeViewState(
        tab = HomeTab.entries.firstOrNull { it.name == savedStateHandle.get<String>("homeTab") }
            ?: HomeTab.HOME,
        questionSearch = savedStateHandle.get<String>("questionSearch").orEmpty()
    ))
    val stateFlow = _stateFlow.asStateFlow()

    private val _uiEventsFlow = MutableSharedFlow<UiEvent>()
    val uiEventsFlow = _uiEventsFlow.asSharedFlow()
    private var dashboardJob: Job? = null
    private var topicsJob: Job? = null

    init {
        loadDashboard()
        if (_stateFlow.value.tab == HomeTab.TOPICS) loadTopics()
    }

    fun selectTab(tab: HomeTab) {
        savedStateHandle["homeTab"] = tab.name
        _stateFlow.update { it.copy(tab = tab) }
        if (tab == HomeTab.TOPICS && _stateFlow.value.topics == ContentViewState.NotLoaded) loadTopics()
    }

    fun openTopicQuestions(topicName: String) {
        updateQuestionSearch(topicName)
        selectTab(HomeTab.QUESTIONS)
    }

    fun updateQuestionSearch(query: String) {
        savedStateHandle["questionSearch"] = query
        _stateFlow.update { it.copy(questionSearch = query) }
    }

    fun loadDashboard() {
        if (dashboardJob?.isActive == true) return
        _stateFlow.update { it.copy(dashboard = ContentViewState.Loading) }
        dashboardJob = viewModelScope.launch {
            val result = getDashboardUseCase(Unit)
            _stateFlow.update {
                it.copy(dashboard = result.toViewState(),
                    sessionExpired = it.sessionExpired || result == ContentResult.SessionExpired)
            }
            if (result is ContentResult.Error) _uiEventsFlow.emit(UiEvent.Toast(result.message))
        }
    }

    fun loadTopics() {
        if (topicsJob?.isActive == true) return
        _stateFlow.update { it.copy(topics = ContentViewState.Loading) }
        topicsJob = viewModelScope.launch {
            val result = getTopicsUseCase(Unit)
            _stateFlow.update {
                it.copy(topics = result.toViewState(),
                    sessionExpired = it.sessionExpired || result == ContentResult.SessionExpired)
            }
            if (result is ContentResult.Error) _uiEventsFlow.emit(UiEvent.Toast(result.message))
        }
    }
}
