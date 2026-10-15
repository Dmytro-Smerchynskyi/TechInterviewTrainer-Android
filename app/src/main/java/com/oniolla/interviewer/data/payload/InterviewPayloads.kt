package com.oniolla.interviewer.data.payload

import com.google.gson.annotations.SerializedName
import com.oniolla.interviewer.data.model.PracticeGrade

data class CreateInterviewPayload(@SerializedName("grade") val grade: PracticeGrade)
data class InterviewAnswerPayload(
    @SerializedName("questionId") val questionId: Long,
    @SerializedName("answer") val answer: String
)
