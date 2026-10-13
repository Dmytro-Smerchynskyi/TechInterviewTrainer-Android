package com.oniolla.interviewer.domain

import com.oniolla.interviewer.data.HomeRepository
import com.oniolla.interviewer.data.model.QuestionsResponse
import javax.inject.Inject

enum class FavoritesFilter { SAVED, DUE, MASTERED }

data class FavoriteQuestionsRequest(
    val filter: FavoritesFilter = FavoritesFilter.SAVED,
    val cursor: String? = null
)

class GetFavoriteQuestionsUseCase @Inject constructor(
    private val homeRepository: HomeRepository
) : UseCase<FavoriteQuestionsRequest, ContentResult<QuestionsResponse>> {
    override suspend fun invoke(input: FavoriteQuestionsRequest): ContentResult<QuestionsResponse> = loadContent {
        when (input.filter) {
            FavoritesFilter.SAVED -> homeRepository.getSavedQuestions(input.cursor)
            FavoritesFilter.DUE -> homeRepository.getDueQuestions(input.cursor)
            FavoritesFilter.MASTERED -> homeRepository.getQuestions(
                topicId = null, search = null, cursor = input.cursor, mastered = true
            )
        }
    }
}
