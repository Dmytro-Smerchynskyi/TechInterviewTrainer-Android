package com.oniolla.interviewer.data.model

enum class Specialization {
    ANDROID, IOS, DOTNET, FRONTEND, BACKEND, QA, DATA_ML, DEVOPS;

    val code: String
        get() = name
}
