package org.mozilla.msrp.platform.vertical.news


import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.mozilla.msrp.platform.user.RssApiInfo
import org.springframework.context.annotation.Bean
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.simplexml.SimpleXmlConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import javax.inject.Inject
import javax.inject.Named

interface GoogleRssFeedClient {
    @GET("rss")
    fun rss(@Query("hl") language: String): Call<GoogleRss>

    @GET("rss/headlines/section/topic/{topic}?pz=1&cf=all")
    fun rss(@Path("topic") topic: String, @Query("hl") hl: String, @Query("gl") gl: String, @Query("ceid") ceid: String): Call<GoogleRss>

}

@Named
class GoogleRssFeedClientConfiguration {

    @Inject
    lateinit var rssApiInfo: RssApiInfo

    @Bean
    fun GoogleRssFeedClientFactory(): GoogleRssFeedClient {
        val interceptor = HttpLoggingInterceptor()
        interceptor.level = HttpLoggingInterceptor.Level.BODY
        val client = OkHttpClient.Builder().addInterceptor(interceptor).build()

        return Retrofit.Builder()
                .baseUrl(rssApiInfo.google)
                .client(client)
                .addConverterFactory(SimpleXmlConverterFactory.create())
                .build()
                .create(GoogleRssFeedClient::class.java)
    }
}