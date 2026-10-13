package com.oniolla.interviewer.domain

import com.oniolla.interviewer.data.AuthRepository
import com.oniolla.interviewer.data.model.PreparationLanguage
import javax.inject.Inject

class GetPreparationLanguagesUseCase @Inject constructor(
    private val authRepository: AuthRepository
) : UseCase<Unit, ContentResult<List<PreparationLanguage>>> {
    override suspend fun invoke(input: Unit): ContentResult<List<PreparationLanguage>> = loadContent {
        authRepository.getPreparationLanguages().items.filter { it.contentAvailable }.distinctBy { it.code }
    }
}
