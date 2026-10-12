package com.oniolla.interviewer.domain

import com.oniolla.interviewer.data.HomeRepository
import com.oniolla.interviewer.data.model.TopicResponse
import javax.inject.Inject

class GetTopicsUseCase @Inject constructor(
    private val homeRepository: HomeRepository
) : UseCase<Unit, ContentResult<List<TopicResponse>>> {
    override suspend fun invoke(input: Unit): ContentResult<List<TopicResponse>> = loadContent {
        homeRepository.getTopics().filter { it.active }.sortedBy { it.sortOrder }
    }
}
