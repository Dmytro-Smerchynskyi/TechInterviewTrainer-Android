package com.oniolla.interviewer.data

class QuestionEvaluationLimitException(val error: CustomError) : ApiException(error.message)
