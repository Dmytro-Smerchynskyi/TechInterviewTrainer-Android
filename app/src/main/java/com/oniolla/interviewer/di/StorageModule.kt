package com.oniolla.interviewer.di

import android.content.Context
import androidx.room.Room
import com.oniolla.interviewer.data.local.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object StorageModule {
    @Provides
    @Singleton
    fun provideTopicDatabase(@ApplicationContext context: Context): TopicDatabase =
        Room.databaseBuilder(context, TopicDatabase::class.java, "topics.db").build()

    @Provides
    fun provideTopicDao(database: TopicDatabase): TopicDao = database.topicDao()

    @Provides
    @Singleton
    fun provideTopicCache(dao: TopicDao): TopicCache = RoomTopicCache(dao)
}
