package com.kissangram.usecase

import com.kissangram.model.UserStories
import com.kissangram.repository.StoryRepository

/**
 * Use case for getting the story bar (stories from followed users)
 */
class GetStoryBarUseCase(
    private val storyRepository: StoryRepository
) {
    @Throws(Exception::class)
    suspend operator fun invoke(): List<UserStories> {
        return storyRepository.getStoryBar()
    }
}
