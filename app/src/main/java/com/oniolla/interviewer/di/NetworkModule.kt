package com.oniolla.interviewer.di

import com.oniolla.interviewer.data.Api
import com.oniolla.interviewer.data.TokenStorage
import com.oniolla.interviewer.data.SessionTokenStorage
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.adapter.rxjava3.RxJava3CallAdapterFactory
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideSessionTokenStorage(storage: TokenStorage): SessionTokenStorage = storage

    @Provides
    @Singleton
    fun provideApi(tokenStorage: SessionTokenStorage): Api {
        val retrofit = Retrofit
            .Builder()
            .baseUrl("http://192.168.0.117:8080/")
            .addConverterFactory(GsonConverterFactory.create())
            .addCallAdapterFactory(RxJava3CallAdapterFactory.create())
            .client(OkHttpClient.Builder()
                .addInterceptor {
                    val request = it.request().newBuilder()
                    tokenStorage.accessToken?.takeIf { token -> token.isNotBlank() }?.let { token ->
                        request.header("Authorization", "Bearer $token")
                    }
                    it.proceed(request.build())
                }
                .build())
            .build()

        return retrofit.create(Api::class.java)
    }
}