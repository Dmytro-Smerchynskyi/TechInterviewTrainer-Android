package com.oniolla.interviewer.domain

import com.oniolla.interviewer.data.HomeRepository
import com.oniolla.interviewer.data.model.QuestionsResponse
import javax.inject.Inject

data class QuestionsRequest(
    val topicId: Long? = null,
    val search: String = "",
    val cursor: String? = null
)

class GetQuestionsUseCase @Inject constructor(
    private val homeRepository: HomeRepository
) : UseCase<QuestionsRequest, ContentResult<QuestionsResponse>> {
    override suspend fun invoke(input: QuestionsRequest): ContentResult<QuestionsResponse> = loadContent {
        homeRepository.getQuestions(
            topicId = input.topicId,
            search = input.search.trim().takeIf { it.isNotEmpty() },
            cursor = input.cursor
        )
    }
}
