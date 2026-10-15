package com.oniolla.interviewer.data

import com.oniolla.interviewer.data.model.*
import com.oniolla.interviewer.data.payload.*
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import javax.inject.Inject

class InterviewRepository @Inject constructor(
    private val api: Api,
    private val session: SessionRefreshCoordinator
) {
    suspend fun activeInterview(): LastInterview? {
        var cursor: String? = null
        val seen = mutableSetOf<String>()
        do {
            val page = session.authorizedApiCall { api.interviewHistory(cursor = cursor, session = it) }
            currentCoroutineContext().ensureActive()
            page.items.firstOrNull { it.status == "IN_PROGRESS" }?.let { return it }
            cursor = page.nextCursor?.takeIf { it.isNotBlank() }
            check(cursor == null || seen.add(cursor)) { "Interview history returned a repeated cursor" }
        } while (cursor != null)
        return null
    }

    suspend fun create(grade: PracticeGrade): InterviewResponse =
        session.authorizedApiCall { api.createInterview(CreateInterviewPayload(grade), it) }

    suspend fun get(id: String): InterviewResponse = session.authorizedApiCall { api.interview(id, it) }

    suspend fun current(id: String): CurrentInterviewQuestion =
        session.authorizedApiCall { api.currentInterviewQuestion(id, it) }

    suspend fun finish(id: String): InterviewResponse = session.authorizedApiCall {
        try { apiCall { api.finishInterview(id, it) } }
        catch (error: ApiException) {
            if (error.customCode() == "INTERVIEW_ALREADY_FINISHED") api.interviewResult(id, it)
            else throw error
        }
    }

    suspend fun answer(id: String, questionId: Long, answer: String): QuestionEvaluationResponse =
        session.authorizedApiCall {
            try { apiCall { api.answerInterview(id, InterviewAnswerPayload(questionId, answer), it) } }
            catch (error: ApiException) {
                val custom = error.customErrorBody()
                if (custom?.code == "AI_EVALUATION_LIMIT_EXCEEDED") throw QuestionEvaluationLimitException(custom)
                throw error
            }
        }
}

private fun ApiException.customErrorBody(): CustomError? = when (this) {
    is ApiException.BadRequest -> customError
    is ApiException.Unauthorized -> customError
    is ApiException.Forbidden -> customError
    is ApiException.NotFound -> customError
    is ApiException.ServerError -> customError
    is ApiException.Unknown -> customError
    else -> null
}
private fun ApiException.customCode() = customErrorBody()?.code
