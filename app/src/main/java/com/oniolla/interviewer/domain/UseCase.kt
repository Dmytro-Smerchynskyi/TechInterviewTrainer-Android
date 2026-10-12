package com.oniolla.interviewer.domain

interface UseCase<In, Out> {
    suspend operator fun invoke(input: In): Out
}