// app/src/main/java/com/guardianos/core/audit/api/ExodusApi.kt
package com.guardianos.core.audit.api

import com.guardianos.core.audit.model.ExodusReport
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path

interface ExodusApi {
    @GET("reports/{packageName}/latest/")
    suspend fun getAppReport(@Path("packageName") packageName: String): ExodusReport
}

object ExodusClient {
    private const val BASE_URL = "https://reports.exodus-privacy.eu.org/api/"
    
    val instance: ExodusApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ExodusApi::class.java)
    }
}
