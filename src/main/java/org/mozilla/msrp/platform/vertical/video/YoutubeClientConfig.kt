package org.mozilla.msrp.platform.vertical.video

import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.services.youtube.YouTube
import org.springframework.context.annotation.Bean
import javax.inject.Named

@Named
class YoutubeClientConfig {
    @Bean
    fun youtubeClientFactory(): YouTube {
        return YouTube(NetHttpTransport(), JacksonFactory()) {
        }
    }
}