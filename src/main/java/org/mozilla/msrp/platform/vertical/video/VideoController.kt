package org.mozilla.msrp.platform.vertical.video

import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import org.mozilla.msrp.platform.common.property.VideoProperties
import org.mozilla.msrp.platform.util.logger
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@RestController
class VideoController @Inject constructor(
        private val youtubeService: YoutubeService,
        private val videoProperties: VideoProperties) {

    private val log = logger()

    @GetMapping("/api/v1/video")
    internal fun videoListByKeyword(
            @RequestParam(value = "query") query: String,
            @RequestParam(value = "locale") locale: String,
            @RequestParam(value = "limit", required = false, defaultValue = "") limit: String
    ): ResponseEntity<List<VideoItem>> {

        log.info("[VIDEO]====loading videos [$query][$locale][$limit]")

        val maxResult = limit.toLongOrDefault(0L)
        val key = query + delimiters + locale + delimiters + maxResult
        val cache = cacheVideos.get(key)
        log.info("[VIDEO]====cache videos key[${key}]")

        if (cache == null || cache.isEmpty()) {
            return ResponseEntity(listOf(), HttpStatus.NO_CONTENT)
        }
        log.info("[VIDEO]====cache videos [${cache.size}]")

        return ResponseEntity(cache, HttpStatus.OK)
    }

    private fun String.toLongOrDefault(default: Long): Long {
        return if (isNotEmpty()) {
            try {
                toLong()
            } catch (e: NumberFormatException) {
                default
            }
        } else {
            default
        }
    }

    private val cacheVideos = CacheBuilder.newBuilder()
            .maximumSize(videoProperties.cacheSize)
            .refreshAfterWrite(videoProperties.cacheTtl, TimeUnit.HOURS)
            .recordStats()
            .build(
                    object : CacheLoader<String, List<VideoItem>>() {
                        override fun load(key: String): List<VideoItem> {
                            val split = key.split(delimiters)
                            if (split.size != 3) {
                                return listOf()
                            }
                            return youtubeService.getVideoList(split[0], split[1], split[2].toLong()) ?: listOf()
                        }
                    }
            )

    companion object {
        private const val delimiters = "=="
    }
}