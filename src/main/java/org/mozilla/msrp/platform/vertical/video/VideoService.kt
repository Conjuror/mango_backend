package org.mozilla.msrp.platform.vertical.video

import javax.inject.Inject
import javax.inject.Named

@Named
class YoutubeService @Inject constructor(private val youtubeRepository: YoutubeRepository) {

    fun getVideoList(query: String, locale: String, maxResult: Long): List<VideoItem>? {
        return youtubeRepository.videoList(query, locale, maxResult)
    }
}