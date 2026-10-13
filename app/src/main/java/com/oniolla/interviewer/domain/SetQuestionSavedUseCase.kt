package com.oniolla.interviewer.domain

import com.oniolla.interviewer.data.HomeRepository
import javax.inject.Inject

data class QuestionSavedRequest(val questionId: Long, val saved: Boolean)

class SetQuestionSavedUseCase @Inject constructor(
    private val homeRepository: HomeRepository
) : UseCase<QuestionSavedRequest, ContentResult<Unit>> {
    override suspend fun invoke(input: QuestionSavedRequest): ContentResult<Unit> = loadContent {
        homeRepository.setQuestionSaved(input.questionId, input.saved)
    }
}
