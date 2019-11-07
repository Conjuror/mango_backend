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
import javax.inject.Inject
import javax.inject.Named

interface DetikRssFeedClient {

    @GET("index.php/{topic}")
    fun rss(@Path("topic") topic: String): Call<DetikRss>

}

@Named
class DetikRssFeedClientConfig {

    @Inject
    lateinit var rssApiInfo: RssApiInfo

    @Bean
    fun DetikRssFeedClientFactory(): DetikRssFeedClient {
        val interceptor = HttpLoggingInterceptor()
        interceptor.level = HttpLoggingInterceptor.Level.BODY
        val client = OkHttpClient.Builder().addInterceptor(interceptor).build()

        return Retrofit.Builder()
            .baseUrl(rssApiInfo.detik)
            .client(client)
            .addConverterFactory(SimpleXmlConverterFactory.create())
            .build()
            .create(DetikRssFeedClient::class.java)
    }
}