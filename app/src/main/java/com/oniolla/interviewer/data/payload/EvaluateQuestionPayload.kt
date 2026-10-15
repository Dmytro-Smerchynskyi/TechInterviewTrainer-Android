package com.oniolla.interviewer.data.payload

import com.google.gson.annotations.SerializedName
import com.oniolla.interviewer.data.model.PracticeGrade

data class EvaluateQuestionPayload(
    @SerializedName("answer") val answer: String,
    @SerializedName("grade") val grade: PracticeGrade
)
