package org.mozilla.msrp.platform.vertical.video

import com.google.api.services.youtube.YouTube
import org.mozilla.msrp.platform.util.hash
import org.mozilla.msrp.platform.util.logger
import javax.inject.Inject
import javax.inject.Named

@Named
class YoutubeRepository @Inject constructor(private val youtube: YouTube) {

    @Inject
    lateinit var youtubeApiInfo: YoutubeApiInfo

    val log = logger()

    fun videoList(query: String, locale: String, maxResult: Long): List<VideoItem>? {
        return try {
            log.info("[Youtube]====start retrieving list")
            val searchRequest = youtube.search().list("id,snippet")
            searchRequest.key = youtubeApiInfo.apiKey
            searchRequest.q = query
            searchRequest.type = "video"
            searchRequest.order = "viewCount"
            searchRequest.relevanceLanguage = locale
            if (maxResult > 0) {
                searchRequest.maxResults = maxResult
            }

            val searchResponse = searchRequest.execute()
            val searchResultList = searchResponse.items
            val videoList = if (!searchResultList.isNullOrEmpty()) {
                // Both video duration and view count information cannot be retrieved from the search api.
                // So, made another video list api call with all the video id from the previous search result
                val idList: String = searchResultList.map { it.id.videoId }.reduce { accumulated, id -> "$accumulated,$id" }
                val videoDetailRequest = youtube.videos().list("contentDetails,statistics")
                videoDetailRequest.key = youtubeApiInfo.apiKey
                videoDetailRequest.id = idList
                val videoDetailResponse = videoDetailRequest.execute()
                val videoDetailResultList = videoDetailResponse.items

                val results = ArrayList<VideoItem>()
                searchResultList.forEach { result ->
                    val videoDetail = videoDetailResultList.firstOrNull { it.id == result.id.videoId }
                    results.add(
                            VideoItem(
                                    result.snippet.title,
                                    result.snippet.channelTitle,
                                    result.snippet.publishedAt.toString(),
                                    result.snippet.thumbnails.default.url,
                                    videoDetail?.contentDetails?.duration ?: "",
                                    youtubeApiInfo.watchUrl + result.id.videoId,
                                    videoDetail?.statistics?.viewCount?.toString() ?: "",
                                    (youtubeApiInfo.watchUrl + result.id.videoId).hash(),
                                    youtubeApiInfo.source
                            )
                    )
                }
                results
            } else {
                null
            }
            log.info("[Youtube]====end retrieving list ${videoList?.size}")
            videoList
        } catch (e: Exception) {
            log.error("[Youtube]====Exception: $e")
            emptyList()
        }
    }
}