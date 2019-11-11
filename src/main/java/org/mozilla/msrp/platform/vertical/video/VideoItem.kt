package org.mozilla.msrp.platform.vertical.video

data class VideoItem(
        val title: String,
        val channelTitle: String,
        val publishedAt: String,
        val thumbnail: String,
        val duration: String,
        val link: String,
        val viewCount: String,
        val componentId: String,
        val source: String
)