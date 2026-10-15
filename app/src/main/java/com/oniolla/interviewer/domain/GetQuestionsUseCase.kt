package com.oniolla.interviewer.domain

import com.oniolla.interviewer.data.HomeRepository
import com.oniolla.interviewer.data.model.QuestionsResponse
import javax.inject.Inject

enum class QuestionDifficultyFilter(val apiValue: String?) {
    ALL(null), EASY("EASY"), MEDIUM("MEDIUM"), HARD("HARD")
}

data class QuestionsRequest(
    val topicId: Long? = null,
    val search: String = "",
    val cursor: String? = null,
    val difficulty: QuestionDifficultyFilter = QuestionDifficultyFilter.ALL
)

class GetQuestionsUseCase @Inject constructor(
    private val homeRepository: HomeRepository
) : UseCase<QuestionsRequest, ContentResult<QuestionsResponse>> {
    override suspend fun invoke(input: QuestionsRequest): ContentResult<QuestionsResponse> = loadContent {
        homeRepository.getQuestions(
            topicId = input.topicId,
            search = input.search.trim().takeIf { it.isNotEmpty() },
            cursor = input.cursor,
            difficulty = input.difficulty.apiValue
        )
    }
}
