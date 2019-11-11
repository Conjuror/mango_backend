package org.mozilla.msrp.platform.vertical.video

import com.google.cloud.firestore.Firestore
import org.mozilla.msrp.platform.util.logger
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.DependsOn
import java.util.concurrent.ExecutionException

@Configuration
open class YoutubeApiConfiguration {

    private val log = logger()

    @Bean
    @DependsOn("Firestore")
    open fun provideYoutubeApiInfo(firestore: Firestore): YoutubeApiInfo? {
        log.info(" --- Bean Creation YoutubeApiInfo ---")
        try {
            val query = firestore.collection("settings").get()
            val querySnapshot = query.get()
            val documents = querySnapshot.documents
            for (document in documents) {

                val apiKey = document.getString("youtube_api_key")
                        ?: throw IllegalStateException("Youtube api key is not set")

                log.info("Get YoutubeApiInfo settings --- success ---")
                return YoutubeApiInfo(apiKey, WATCH_URL, SOURCE)
            }
        } catch (e: InterruptedException) {
            log.error("Get YoutubeApiInfo settings -- failed :$e")
        } catch (e: ExecutionException) {
            log.error("Get YoutubeApiInfo settings -- failed :$e")
        }

        log.error("Get YoutubeApiInfo settings -- failed, shouldn't reach this line --- ")
        return null
    }

    companion object {
        private const val WATCH_URL = "https://www.youtube.com/watch?v="
        private const val SOURCE = "youtube"
    }
}

class YoutubeApiInfo(
        val apiKey: String,
        val watchUrl: String,
        val source: String
)
