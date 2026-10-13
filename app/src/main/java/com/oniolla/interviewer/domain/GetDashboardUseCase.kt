package com.oniolla.interviewer.domain

import com.oniolla.interviewer.data.AuthRepository
import com.oniolla.interviewer.data.HomeRepository
import com.oniolla.interviewer.data.model.DashboardResponse
import com.oniolla.interviewer.data.model.MeResponse
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject

data class DashboardContent(val profile: MeResponse, val dashboard: DashboardResponse)

class GetDashboardUseCase @Inject constructor(
    private val homeRepository: HomeRepository,
    private val authRepository: AuthRepository
) : UseCase<Unit, ContentResult<DashboardContent>> {
    override suspend fun invoke(input: Unit): ContentResult<DashboardContent> = loadContent {
        coroutineScope {
            val profile = async { authRepository.getSessionProfile() }
            val dashboard = async { homeRepository.getDashboard() }
            DashboardContent(profile.await(), dashboard.await())
        }
    }
}
