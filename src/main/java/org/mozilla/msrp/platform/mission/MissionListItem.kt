package org.mozilla.msrp.platform.mission

/**
 * (All fields are just draft and are subject to change)
 * Client-facing mission class, this class should contains
 * 1. mid
 * 2. name, description in user's language (if support)
 * 3. status such as join, dropped, etc
 * 4. progress
 */
data class MissionListItem(
        val mid: String,
        val title: String,
        val description: String,
        val joinEndpoint: String,
        val redeemEndpoint: String,
        val events: List<String>,
        val expiredDate: Long,
        val redeemEndDate: Long,
        val status: JoinStatus,
        val minVersion: Int,
        var progress: Map<String, Any>,
        val important: Boolean,
        val missionType: String,
        val joinEndDate: Long,
        val imageUrl: String,
        val rewardExpiredDate: Long,
        val parameters: Map<String, Any>    // daily mission will only expose one key: totalDays
)
