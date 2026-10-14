package com.oniolla.interviewer.domain

import com.oniolla.interviewer.data.HomeRepository
import com.oniolla.interviewer.data.model.QuestionAnswerResponse
import javax.inject.Inject

class GetQuestionAnswerUseCase @Inject constructor(private val repository: HomeRepository) :
    UseCase<Long, ContentResult<QuestionAnswerResponse>> {
    override suspend fun invoke(input: Long): ContentResult<QuestionAnswerResponse> = loadContent {
        repository.getQuestionAnswer(input).also {
            check(it.questionId == input) { "Answer belongs to another question" }
        }
    }

    fun cachedUsage() = repository.cachedUsage()
    fun currentUsage() = repository.currentUsage()
    suspend fun usage() = loadContent { repository.getQuestionUsage() }
}
