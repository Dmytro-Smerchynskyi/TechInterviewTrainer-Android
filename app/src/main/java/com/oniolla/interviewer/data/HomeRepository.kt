package com.oniolla.interviewer.data

import com.oniolla.interviewer.data.model.DashboardResponse
import com.oniolla.interviewer.data.model.TopicResponse
import javax.inject.Inject

class HomeRepository @Inject constructor(private val api: Api) {
    suspend fun getDashboard(): DashboardResponse = apiCall { api.dashboard() }
    suspend fun getTopics(): List<TopicResponse> = apiCall { api.topics() }
}
