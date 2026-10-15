package com.oniolla.interviewer.domain

import com.oniolla.interviewer.data.ApiException
import com.oniolla.interviewer.data.HomeRepository
import com.oniolla.interviewer.data.QuestionEvaluationLimitException
import com.oniolla.interviewer.data.toApiException
import com.oniolla.interviewer.data.model.PracticeGrade
import com.oniolla.interviewer.data.model.QuestionEvaluationResponse
import com.oniolla.interviewer.data.payload.EvaluateQuestionPayload
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

data class EvaluateQuestionRequest(val questionId: Long, val answer: String, val grade: PracticeGrade)

sealed interface EvaluationResult {
    data class Success(val evaluation: QuestionEvaluationResponse) : EvaluationResult
    data class LimitReached(val message: String?, val subscriptionRequired: Boolean) : EvaluationResult
    data class Error(val message: String) : EvaluationResult
    data object SessionExpired : EvaluationResult
}

class EvaluateQuestionUseCase @Inject constructor(private val repository: HomeRepository) :
    UseCase<EvaluateQuestionRequest, EvaluationResult> {
    override suspend fun invoke(input: EvaluateQuestionRequest): EvaluationResult = try {
        val answer = input.answer.trim()
        require(answer.isNotEmpty() && answer.length <= 10_000) { "Enter an answer of up to 10,000 characters." }
        val response = repository.evaluateQuestion(input.questionId, EvaluateQuestionPayload(answer, input.grade))
        check(response.questionId == null || response.questionId == input.questionId) {
            "Evaluation belongs to another question"
        }
        EvaluationResult.Success(response)
    } catch (error: CancellationException) {
        throw error
    } catch (error: QuestionEvaluationLimitException) {
        EvaluationResult.LimitReached(error.message, error.error.subscriptionRequired == true)
    } catch (error: Exception) {
        when (val apiError = error.toApiException()) {
            is ApiException.Unauthorized -> EvaluationResult.SessionExpired
            is ApiException.NetworkError -> EvaluationResult.Error("Unable to connect. Your answer is kept here. Please try again.")
            else -> EvaluationResult.Error(apiError.message ?: "Unable to evaluate your answer. Please try again.")
        }
    }

    fun currentUsage() = repository.currentUsage()
    fun cachedUsage() = repository.cachedUsage()
    suspend fun refreshUsage() = loadContent { repository.getQuestionUsage() }
}
