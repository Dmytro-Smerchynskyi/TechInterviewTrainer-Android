package com.oniolla.interviewer.domain

import com.oniolla.interviewer.data.HomeRepository
import com.oniolla.interviewer.data.model.MeResponse
import com.oniolla.interviewer.data.model.TopicResponse
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class TopicCatalogUseCase @Inject constructor(private val repository: HomeRepository) {
    fun scope(profile: MeResponse): String =
        listOf(profile.id, profile.specialization.orEmpty(), profile.preparationLanguage.orEmpty())
            .joinToString("|") { "${it.length}:$it" }

    fun observe(scope: String): Flow<List<TopicResponse>?> = repository.observeTopicCatalog(scope)

    suspend fun refresh(scope: String): ContentResult<Unit> = loadContent {
        repository.refreshTopicCatalog(scope)
    }
}
