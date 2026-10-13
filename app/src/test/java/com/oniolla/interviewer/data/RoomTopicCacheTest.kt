package com.oniolla.interviewer.data

import android.app.Application
import androidx.room.Room
import com.oniolla.interviewer.data.local.RoomTopicCache
import com.oniolla.interviewer.data.local.TopicDatabase
import com.oniolla.interviewer.data.model.TopicResponse
import com.oniolla.interviewer.data.model.TopicVisual
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class RoomTopicCacheTest {
    private lateinit var database: TopicDatabase
    private lateinit var cache: RoomTopicCache
    private val topics = listOf(
        TopicResponse(7, "ANDROID", "flow", "Flow", "Streams", 2,
            visual = TopicVisual("/flow.svg", 3, "#123456", "#ABCDEF"),
            answered = 12, averageScore = 60.5, mastery = 0.0),
        TopicResponse(1, "ANDROID", "kotlin", "Kotlin", sortOrder = 1)
    )

    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), TopicDatabase::class.java).build()
        cache = RoomTopicCache(database.topicDao())
    }

    @After fun tearDown() { database.close() }

    @Test fun storesAndReadsAllFieldsInServerOrderWithoutLosingNullsOrZero() = runBlocking {
        assertNull(cache.observe("user|ANDROID|en").first())
        assertTrue(cache.replaceIfChanged("user|ANDROID|en", topics))
        assertEquals(topics.sortedBy { it.sortOrder }, cache.observe("user|ANDROID|en").first())
    }

    @Test fun unchangedResponseDoesNotRewriteAndChangedResponseRemovesDeletedTopics() = runBlocking {
        assertTrue(cache.replaceIfChanged("scope", topics))
        assertFalse(cache.replaceIfChanged("scope", topics.reversed()))
        val changed = listOf(topics.first().copy(name = "Updated Flow", mastery = 42.5,
            visual = topics.first().visual!!.copy(iconVersion = 4)))
        assertTrue(cache.replaceIfChanged("scope", changed))
        assertEquals(changed, cache.observe("scope").first())
        assertFalse(cache.replaceIfChanged("scope", changed))
    }

    @Test fun emptyResponseClearsOldTopicsAndRemainsAValidLoadedCatalog() = runBlocking {
        cache.replaceIfChanged("scope", topics)
        assertTrue(cache.replaceIfChanged("scope", emptyList()))
        assertEquals(emptyList<TopicResponse>(), cache.observe("scope").first())
        assertFalse(cache.replaceIfChanged("scope", emptyList()))
    }

    @Test fun cachesForUsersSpecializationsAndLanguagesAreIndependent() = runBlocking {
        cache.replaceIfChanged("user1|ANDROID|en", topics)
        cache.replaceIfChanged("user2|IOS|uk", listOf(topics.first().copy(name = "iOS")))
        assertEquals(topics.sortedBy { it.sortOrder }, cache.observe("user1|ANDROID|en").first())
        assertEquals("iOS", cache.observe("user2|IOS|uk").first()!!.single().name)
        assertNull(cache.observe("user1|ANDROID|uk").first())
    }

    @Test fun diskCacheSurvivesClosingAndReopeningDatabase() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val name = "topic-cache-persistence-test.db"
        context.deleteDatabase(name)
        try {
            val first = Room.databaseBuilder(context, TopicDatabase::class.java, name).build()
            try { RoomTopicCache(first.topicDao()).replaceIfChanged("scope", topics) }
            finally { first.close() }
            val reopened = Room.databaseBuilder(context, TopicDatabase::class.java, name).build()
            try { assertEquals(topics.sortedBy { it.sortOrder }, RoomTopicCache(reopened.topicDao()).observe("scope").first()) }
            finally { reopened.close() }
        } finally { context.deleteDatabase(name) }
    }
}
