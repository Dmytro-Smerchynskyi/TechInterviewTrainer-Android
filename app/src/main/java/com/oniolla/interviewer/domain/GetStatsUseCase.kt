package com.oniolla.interviewer.domain

import com.oniolla.interviewer.data.HomeRepository
import com.oniolla.interviewer.data.model.StatsResponse
import javax.inject.Inject

data class StatsRequest(val timeZone: String? = null, val weekStart: String? = null)

class GetStatsUseCase @Inject constructor(
    private val homeRepository: HomeRepository
) : UseCase<StatsRequest, ContentResult<StatsResponse>> {
    fun clearCache() = homeRepository.clearStatsCache()

    override suspend fun invoke(input: StatsRequest): ContentResult<StatsResponse> = loadContent {
        homeRepository.getStats(input.timeZone, input.weekStart)
    }
}
