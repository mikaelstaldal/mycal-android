package nu.staldal.mycal.data.api

import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {
    private var currentBaseUrl: String? = null
    private var currentUsername: String? = null
    private var currentPassword: String? = null
    private var apiService: ApiService? = null

    fun getApiService(baseUrl: String, username: String, password: String): ApiService? {
        if (baseUrl.isBlank()) return null

        val normalizedUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"

        if (normalizedUrl == currentBaseUrl && username == currentUsername && password == currentPassword && apiService != null) {
            return apiService
        }

        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val requestBuilder = chain.request().newBuilder()
                if (username.isNotBlank()) {
                    requestBuilder.header("Authorization", Credentials.basic(username, password))
                }
                chain.proceed(requestBuilder.build())
            }
            .addInterceptor(logging)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(normalizedUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        apiService = retrofit.create(ApiService::class.java)
        currentBaseUrl = normalizedUrl
        currentUsername = username
        currentPassword = password
        return apiService
    }
}
