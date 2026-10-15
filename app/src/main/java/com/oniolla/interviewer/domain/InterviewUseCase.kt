package com.oniolla.interviewer.domain

import com.oniolla.interviewer.data.*
import com.oniolla.interviewer.data.model.*
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

class InterviewUseCase @Inject constructor(
    private val repository: InterviewRepository,
    private val home: HomeRepository
) {
    suspend fun history() = loadContent { repository.activeInterview() }
    suspend fun create(grade: PracticeGrade) = loadContent { repository.create(grade) }
    suspend fun get(id: String) = loadContent { repository.get(id) }
    suspend fun current(id: String) = loadContent { repository.current(id) }
    suspend fun finish(id: String) = loadContent {
        repository.finish(id).also { check(it.status == "FINISHED") { "Interview is not finished yet" } }
    }
    suspend fun summary() = loadContent { home.getStats(StatsDates.deviceTimeZone(), null) }
    fun usage() = home.cachedUsage()
    fun currentUsage() = home.currentUsage()
    suspend fun refreshUsage() = loadContent { home.getQuestionUsage() }

    suspend fun answer(id: String, questionId: Long, answer: String): EvaluationResult = try {
        require(answer.isNotBlank() && answer.length <= 10_000)
        val result = repository.answer(id, questionId, answer.trim())
        check(result.questionId == null || result.questionId == questionId) { "Evaluation belongs to another question" }
        check(result.interviewId == null || result.interviewId == id) { "Evaluation belongs to another interview" }
        EvaluationResult.Success(result)
    } catch (error: CancellationException) { throw error }
    catch (error: QuestionEvaluationLimitException) {
        EvaluationResult.LimitReached(error.message, error.error.subscriptionRequired == true)
    } catch (error: Exception) {
        when (val api = error.toApiException()) {
            is ApiException.Unauthorized -> EvaluationResult.SessionExpired
            is ApiException.NetworkError -> EvaluationResult.Error("Unable to connect. Your answer is kept here. Please try again.")
            else -> EvaluationResult.Error(api.message ?: "Unable to evaluate your answer.")
        }
    }
}
