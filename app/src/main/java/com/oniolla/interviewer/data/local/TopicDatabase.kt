package com.oniolla.interviewer.data.local

import androidx.room.*
import com.oniolla.interviewer.data.model.TopicResponse
import com.oniolla.interviewer.data.model.TopicVisual
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "topics", primaryKeys = ["scope", "id"])
data class CachedTopic(
    val scope: String,
    val id: Long,
    val specialization: String,
    val slug: String,
    val name: String,
    val description: String?,
    val sortOrder: Int,
    val active: Boolean,
    @Embedded(prefix = "visual_") val visual: TopicVisual?,
    val answered: Int,
    val averageScore: Double?,
    val mastery: Double?
) {
    fun toResponse() = TopicResponse(id, specialization, slug, name, description, sortOrder,
        active, visual, answered, averageScore, mastery)

    companion object {
        fun fromResponse(scope: String, topic: TopicResponse) = CachedTopic(scope, topic.id,
            topic.specialization, topic.slug, topic.name, topic.description, topic.sortOrder,
            topic.active, topic.visual, topic.answered, topic.averageScore, topic.mastery)
    }
}

@Entity(tableName = "topic_catalogs")
data class TopicCatalog(@PrimaryKey val scope: String)

data class CachedTopicCatalog(
    @Embedded val catalog: TopicCatalog,
    @Relation(parentColumn = "scope", entityColumn = "scope") val topics: List<CachedTopic>
)

@Dao
abstract class TopicDao {
    @Transaction
    @Query("SELECT * FROM topic_catalogs WHERE scope = :scope")
    abstract fun observeCatalog(scope: String): Flow<CachedTopicCatalog?>

    @Query("SELECT * FROM topics WHERE scope = :scope ORDER BY sortOrder, id")
    abstract suspend fun topics(scope: String): List<CachedTopic>

    @Query("SELECT EXISTS(SELECT 1 FROM topic_catalogs WHERE scope = :scope)")
    abstract suspend fun hasCatalog(scope: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertCatalog(catalog: TopicCatalog)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertTopics(topics: List<CachedTopic>)

    @Query("DELETE FROM topics WHERE scope = :scope")
    abstract suspend fun deleteTopics(scope: String)

    @Transaction
    open suspend fun replaceIfChanged(scope: String, topics: List<CachedTopic>): Boolean {
        if (hasCatalog(scope) && topics(scope) == topics) return false
        deleteTopics(scope)
        insertCatalog(TopicCatalog(scope))
        insertTopics(topics)
        return true
    }
}

@Database(entities = [CachedTopic::class, TopicCatalog::class], version = 1, exportSchema = false)
abstract class TopicDatabase : RoomDatabase() {
    abstract fun topicDao(): TopicDao
}
