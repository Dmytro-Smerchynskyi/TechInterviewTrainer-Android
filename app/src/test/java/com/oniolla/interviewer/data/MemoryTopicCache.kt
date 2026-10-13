package com.oniolla.interviewer.data

import com.oniolla.interviewer.data.local.TopicCache
import com.oniolla.interviewer.data.model.TopicResponse
import kotlinx.coroutines.flow.MutableStateFlow

internal class MemoryTopicCache : TopicCache {
    private val scopes = mutableMapOf<String, MutableStateFlow<List<TopicResponse>?>>()
    val refreshes = MutableStateFlow(0)
    var writes = 0
        private set
    override fun observe(scope: String) = scopes.getOrPut(scope) { MutableStateFlow(null) }
    override suspend fun replaceIfChanged(scope: String, topics: List<TopicResponse>): Boolean {
        refreshes.value++
        val flow = observe(scope)
        if (flow.value == topics) return false
        flow.value = topics
        writes++
        return true
    }
}
