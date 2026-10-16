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
    suspend fun question(id: Long): ContentResult<com.oniolla.interviewer.data.model.QuestionResponse> = loadContent {
        homeRepository.getQuestion(id).also {
            check(it.id == id) { "Response belongs to another question" }
        }
    }

    override suspend fun invoke(input: QuestionsRequest): ContentResult<QuestionsResponse> = loadContent {
        homeRepository.getQuestions(
            topicId = input.topicId,
            search = input.search.trim().takeIf { it.isNotEmpty() },
            cursor = input.cursor,
            difficulty = input.difficulty.apiValue
        )
    }
}
