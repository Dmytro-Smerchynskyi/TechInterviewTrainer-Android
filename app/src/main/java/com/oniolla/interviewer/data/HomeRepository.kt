package com.oniolla.interviewer.data

import com.oniolla.interviewer.data.local.TopicCache
import com.oniolla.interviewer.data.model.DashboardResponse
import com.oniolla.interviewer.data.model.TopicResponse
import com.oniolla.interviewer.data.model.QuestionsResponse
import javax.inject.Inject

class HomeRepository @Inject constructor(
    private val api: Api,
    private val topicCache: TopicCache
) {
    fun observeTopicCatalog(scope: String) = topicCache.observe(scope)

    suspend fun refreshTopicCatalog(scope: String) {
        val topics = getTopics().filter { it.active }.sortedWith(compareBy({ it.sortOrder }, { it.id }))
        topicCache.replaceIfChanged(scope, topics)
    }

    suspend fun getDashboard(): DashboardResponse = apiCall { api.dashboard() }
    suspend fun getTopics(type: String? = null): List<TopicResponse> = apiCall { api.topics(type) }
    suspend fun getQuestions(
        topicId: Long?,
        search: String?,
        cursor: String?
    ): QuestionsResponse = apiCall {
        api.questions(topicId = topicId, search = search, limit = 20, cursor = cursor)
    }
}
