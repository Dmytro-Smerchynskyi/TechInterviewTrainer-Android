package com.oniolla.interviewer.domain

import com.oniolla.interviewer.data.HomeRepository
import com.oniolla.interviewer.data.model.TopicResponse
import javax.inject.Inject

enum class TopicsFilter(val apiType: String?) { ALL(null), WEAK("weak") }

class GetTopicsUseCase @Inject constructor(
    private val homeRepository: HomeRepository
) : UseCase<TopicsFilter, ContentResult<List<TopicResponse>>> {
    override suspend fun invoke(input: TopicsFilter): ContentResult<List<TopicResponse>> = loadContent {
        homeRepository.getTopics(input.apiType).filter { it.active }.sortedBy { it.sortOrder }
    }
}
