package com.oniolla.interviewer.data

import com.oniolla.interviewer.data.model.DashboardResponse
import com.oniolla.interviewer.data.model.TopicResponse
import com.oniolla.interviewer.data.model.QuestionsResponse
import javax.inject.Inject

class HomeRepository @Inject constructor(private val api: Api) {
    suspend fun getDashboard(): DashboardResponse = apiCall { api.dashboard() }
    suspend fun getTopics(): List<TopicResponse> = apiCall { api.topics() }
    suspend fun getQuestions(
        topicId: Long?,
        search: String?,
        cursor: String?
    ): QuestionsResponse = apiCall {
        api.questions(topicId = topicId, search = search, limit = 20, cursor = cursor)
    }
}
