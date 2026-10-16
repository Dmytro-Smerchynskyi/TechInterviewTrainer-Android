package com.oniolla.interviewer.di

import com.oniolla.interviewer.BuildConfig
import com.oniolla.interviewer.data.API_BASE_URL
import com.oniolla.interviewer.data.Api
import com.oniolla.interviewer.data.TokenStorage
import com.oniolla.interviewer.data.SessionTokenStorage
import com.oniolla.interviewer.data.SessionAuthorizationInterceptor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
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
    fun provideOkHttpClient(tokenStorage: SessionTokenStorage): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(SessionAuthorizationInterceptor(tokenStorage))
            .apply {
                if (BuildConfig.DEBUG) {
                    val httpLogger = HttpLoggingInterceptor().apply {
                        level = HttpLoggingInterceptor.Level.BODY
                        redactHeader("Authorization")
                    }
                    addInterceptor { chain ->
                        // Reauthentication proofs and rotated credentials must never appear in body logs.
                        val path = chain.request().url.encodedPath
                        val sensitive = listOf("/auth/google", "/auth/password/set", "/auth/password/change",
                            "/auth/email/change/request", "/auth/email/change/resend", "/auth/email/change/confirm")
                        if (sensitive.any(path::endsWith)) chain.proceed(chain.request())
                        else httpLogger.intercept(chain)
                    }
                }
            }
            .build()

    @Provides
    @Singleton
    fun provideApi(client: OkHttpClient): Api = Retrofit.Builder()
        .baseUrl(API_BASE_URL)
        .addConverterFactory(GsonConverterFactory.create())
        .addCallAdapterFactory(RxJava3CallAdapterFactory.create())
        .client(client)
        .build()
        .create(Api::class.java)
}
