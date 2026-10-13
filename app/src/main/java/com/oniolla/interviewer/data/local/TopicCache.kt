package com.oniolla.interviewer.data.local

import com.oniolla.interviewer.data.model.TopicResponse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject

interface TopicCache {
    fun observe(scope: String): Flow<List<TopicResponse>?>
    suspend fun replaceIfChanged(scope: String, topics: List<TopicResponse>): Boolean
}

class RoomTopicCache @Inject constructor(private val dao: TopicDao) : TopicCache {
    override fun observe(scope: String) = dao.observeCatalog(scope).map { catalog ->
        catalog?.topics?.sortedWith(compareBy({ it.sortOrder }, { it.id }))?.map { it.toResponse() }
    }.distinctUntilChanged()

    override suspend fun replaceIfChanged(scope: String, topics: List<TopicResponse>) =
        dao.replaceIfChanged(scope, topics.distinctBy { it.id }
            .map { CachedTopic.fromResponse(scope, it) }.sortedWith(compareBy({ it.sortOrder }, { it.id })))
}
