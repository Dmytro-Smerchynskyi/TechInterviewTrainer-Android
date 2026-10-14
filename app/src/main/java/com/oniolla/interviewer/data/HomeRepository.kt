package com.oniolla.interviewer.data

import com.oniolla.interviewer.data.local.TopicCache
import com.oniolla.interviewer.data.model.DashboardResponse
import com.oniolla.interviewer.data.model.StatsResponse
import com.oniolla.interviewer.data.model.TopicResponse
import com.oniolla.interviewer.data.model.QuestionsResponse
import javax.inject.Inject
import kotlinx.coroutines.ensureActive

class HomeRepository @Inject constructor(
    private val api: Api,
    private val topicCache: TopicCache,
    private val sessionRefresh: SessionRefreshCoordinator,
    private val statsCache: SessionStatsCache = SessionStatsCache()
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

    suspend fun getDashboard(): DashboardResponse = sessionRefresh.authorizedApiCall { api.dashboard(it) }
    suspend fun getTopics(type: String? = null): List<TopicResponse> = sessionRefresh.authorizedApiCall { api.topics(type, it) }
    suspend fun getSavedQuestions(cursor: String?): QuestionsResponse =
        sessionRefresh.authorizedApiCall { api.savedQuestions(cursor = cursor, session = it) }

    suspend fun getDueQuestions(cursor: String?): QuestionsResponse =
        sessionRefresh.authorizedApiCall { api.dueQuestions(cursor = cursor, session = it) }

    suspend fun setQuestionSaved(questionId: Long, saved: Boolean): Unit = sessionRefresh.authorizedApiCall {
        if (saved) api.saveQuestion(questionId, it) else api.unsaveQuestion(questionId, it)
    }

    suspend fun getQuestions(
        topicId: Long?,
        search: String?,
        cursor: String?,
        mastered: Boolean? = null
    ): QuestionsResponse = sessionRefresh.authorizedApiCall {
        api.questions(topicId = topicId, search = search, limit = 20, cursor = cursor, session = it, mastered = mastered)
    }
}
