package com.dicoding.asclepius.data.retrofit

import com.dicoding.asclepius.data.response.Articles
import com.dicoding.asclepius.BuildConfig
import retrofit2.Call
import retrofit2.http.*

interface ApiService {
    @GET("top-headlines")
    fun getArticleList (
        @Query("q") q: String = "cancer",
        @Query("category") category: String = "health",
        @Query("language") language: String = "en",
        @Query("apiKey") apiKey: String = BuildConfig.TOKEN
    ): Call<Articles>

}