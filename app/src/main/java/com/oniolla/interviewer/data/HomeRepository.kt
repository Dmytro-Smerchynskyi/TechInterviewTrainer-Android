package com.oniolla.interviewer.data

import com.oniolla.interviewer.data.local.TopicCache
import com.oniolla.interviewer.data.model.DashboardResponse
import com.oniolla.interviewer.data.model.StatsResponse
import com.oniolla.interviewer.data.model.TopicResponse
import com.oniolla.interviewer.data.model.QuestionsResponse
import com.oniolla.interviewer.data.model.QuestionAnswerResponse
import com.oniolla.interviewer.data.model.QuestionEvaluationResponse
import com.oniolla.interviewer.data.payload.EvaluateQuestionPayload
import javax.inject.Inject
import kotlinx.coroutines.ensureActive

class HomeRepository @Inject constructor(
    private val api: Api,
    private val topicCache: TopicCache,
    private val sessionRefresh: SessionRefreshCoordinator,
    private val statsCache: SessionStatsCache = SessionStatsCache(),
    private val usageRepository: SessionUsageRepository
) {
    fun clearStatsCache() = statsCache.clear()

    fun observeTopicCatalog(scope: String) = topicCache.observe(scope)

    suspend fun refreshTopicCatalog(scope: String) {
        val topics = getTopics().filter { it.active }.sortedWith(compareBy({ it.sortOrder }, { it.id }))
        topicCache.replaceIfChanged(scope, topics)
    }

    suspend fun getStats(timeZone: String?, weekStart: String?): StatsResponse {
        val generation = statsCache.generation()
        var sessionVersion = 0L
        val response = sessionRefresh.authorizedApiCall {
            sessionVersion = it.version
            api.stats(timeZone, weekStart, it)
        }
        kotlinx.coroutines.currentCoroutineContext().ensureActive()
        statsCache.update(generation, sessionVersion, response)
        return response
    }

    fun cachedUsage() = usageRepository.observe()
    fun currentUsage() = usageRepository.current()

    suspend fun getDashboard(): DashboardResponse {
        val revision = usageRepository.revision()
        val (session, response) = sessionRefresh.authorizedApiCall { it to api.dashboard(it) }
        kotlinx.coroutines.currentCoroutineContext().ensureActive()
        return response.copy(usage = usageRepository.cacheDashboard(revision, session, response.usage))
    }
    suspend fun getTopics(type: String? = null): List<TopicResponse> = sessionRefresh.authorizedApiCall { api.topics(type, it) }
    suspend fun getSavedQuestions(cursor: String?): QuestionsResponse =
        sessionRefresh.authorizedApiCall { api.savedQuestions(cursor = cursor, session = it) }

    suspend fun getDueQuestions(cursor: String?): QuestionsResponse =
        sessionRefresh.authorizedApiCall { api.dueQuestions(cursor = cursor, session = it) }

    suspend fun setQuestionSaved(questionId: Long, saved: Boolean): Unit = sessionRefresh.authorizedApiCall {
        if (saved) api.saveQuestion(questionId, it) else api.unsaveQuestion(questionId, it)
    }

    suspend fun getQuestion(questionId: Long): com.oniolla.interviewer.data.model.QuestionResponse =
        sessionRefresh.authorizedApiCall { api.question(questionId, it) }

    suspend fun getQuestionAnswer(questionId: Long): QuestionAnswerResponse =
        sessionRefresh.authorizedApiCall { api.questionAnswer(questionId, it) }

    suspend fun getQuestionUsage() = usageRepository.refresh()

    suspend fun evaluateQuestion(questionId: Long, payload: EvaluateQuestionPayload): QuestionEvaluationResponse =
        sessionRefresh.authorizedApiCall { session ->
            try {
                apiCall { api.evaluateQuestion(questionId, payload, session) }
            } catch (error: ApiException) {
                val custom = when (error) {
                    is ApiException.BadRequest -> error.customError
                    is ApiException.Unauthorized -> error.customError
                    is ApiException.Forbidden -> error.customError
                    is ApiException.NotFound -> error.customError
                    is ApiException.ServerError -> error.customError
                    is ApiException.Unknown -> error.customError
                    else -> null
                }
                if (custom?.code == "AI_EVALUATION_LIMIT_EXCEEDED") {
                    throw QuestionEvaluationLimitException(custom)
                }
                throw error
            }
        }

    suspend fun getQuestions(
        topicId: Long?,
        search: String?,
        cursor: String?,
        mastered: Boolean? = null,
        difficulty: String? = null
    ): QuestionsResponse = sessionRefresh.authorizedApiCall {
        api.questions(topicId = topicId, search = search, limit = 20, cursor = cursor, session = it,
            mastered = mastered, difficulty = difficulty)
    }
}
