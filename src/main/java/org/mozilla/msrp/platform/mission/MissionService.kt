package org.mozilla.msrp.platform.mission

import org.mozilla.msrp.platform.common.getMessageOrEmpty
import org.mozilla.msrp.platform.common.getMessageOrNull
import org.mozilla.msrp.platform.common.isProd
import org.mozilla.msrp.platform.firestore.stringToLocalDateTime
import org.mozilla.msrp.platform.metrics.Metrics
import org.mozilla.msrp.platform.mission.qualifier.MissionProgressDoc
import org.mozilla.msrp.platform.mission.qualifier.MissionQualifier
import org.mozilla.msrp.platform.redward.RewardRepository
import org.mozilla.msrp.platform.util.logger
import org.slf4j.Logger
import org.springframework.context.MessageSource
import org.springframework.core.env.Environment
import org.springframework.http.HttpStatus
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeParseException
import java.util.ArrayList
import java.util.Locale
import javax.inject.Inject
import javax.inject.Named

@Named
class MissionService @Inject constructor(
        private val missionRepository: MissionRepository,
        private val rewardRepository: RewardRepository
) {

    // TODO: Verify user status
    private val isSuspiciousUser: Boolean
        get() = false

    @Inject
    lateinit var missionQualifier: MissionQualifier

    @Inject
    lateinit var missionMessageSource: MessageSource

    @Inject
    lateinit var clock: Clock

    @Inject
    lateinit var environment: Environment

    private val log: Logger = logger()

    fun getMissionsByGroupId(uid: String, groupId: String, zone: ZoneId, locale: Locale): List<MissionListItem> {
        return if (isSuspiciousUser) {
            ArrayList()

        } else {
            this.missionRepository.getMissionsByGroupId(groupId)
                    .filter { isMissionValid(it) }
                    .filter {
                        val joinStatus = missionRepository.getJoinStatus(uid, it.missionType, it.mid)
                        val joinCount = missionRepository.getJoinCount(it.missionType, it.mid)
                        isMissionAvailableForShowing(uid, it, joinStatus, joinCount, clock, zone)
                    }
                    .map { aggregateMissionListItem(uid, it, zone, locale) }
        }
    }

    private fun isMissionValid(mission: MissionDoc): Boolean {
        if (!hasString(mission.titleId)) {
            log.error("title not defined for mission: ${mission.missionType}/${mission.mid}")
            return false
        }

        if (!hasString(mission.descriptionId)) {
            log.error("description not defined for mission: ${mission.missionType}/${mission.mid}")
            return false
        }

        return true
    }

    /**
     * Is this mission available
     */
    internal fun isMissionAvailableForShowing(
            uid: String,
            mission: MissionDoc,
            joinStatus: JoinStatus?,
            joinCount: Int,
            clock: Clock,
            zone: ZoneId
    ): Boolean {

        val missionType = mission.missionType
        val mid = mission.mid

        val logInfo = "isMissionAvailableForShowing: mid=$mid, type=$missionType, uid=$uid, zone=$zone"

        val missionState = try {
            getMissionJoinState(mission, joinStatus, joinCount, clock, zone)

        } catch (e: DateTimeParseException) {
            log.error("$logInfo, msg=illegal mission format")
            return false
        }

        log.info("$logInfo, state=$missionState")

        if (missionState.reachQuota) {
            if (!missionState.isJoined) {
                log.info("$logInfo, msg=quota reached")
                return false
            }
        }

        if (missionState.isBeforeJoinPeriod) {
            log.info("$logInfo, msg=before join start")
            return false
        }

        if (missionState.isAfterJoinPeriod) {
            if (!missionState.isJoined) {
                log.info("$logInfo, msg=after join end")
                return false
            }
        }

        if (missionState.isExpired) {
            if (!missionState.isComplete) {
                log.info("$logInfo, msg=expired")
                return false
            }
        }

        return true
    }

    private fun hasString(resId: String): Boolean {
        return missionMessageSource.getMessageOrNull(resId, Locale.getDefault()) != null
    }

    private fun getMissionTitle(mission: MissionDoc, locale: Locale): String {
        val title = getStringById(mission.titleId, locale)
        return if (environment.isProd) {
            title
        } else {
            "$title (${mission.missionName}, id=${mission.mid.substring(0, 4)}***)"
        }
    }

    private fun aggregateMissionListItem(uid: String, missionDoc: MissionDoc, zone: ZoneId, locale: Locale): MissionListItem {
        val name = getMissionTitle(missionDoc, locale)
        val description = getStringById(missionDoc.descriptionId, locale)

        val joinDoc = missionRepository.getMissionJoinDoc(uid, missionDoc.missionType, missionDoc.mid)
        val joinStatus = joinDoc?.status?.let { it } ?: JoinStatus.New

        val progress = missionQualifier.getProgress(
                uid,
                missionDoc.mid,
                missionDoc.missionTypeEnum
        )

        val important = missionRepository.isImportantMission(missionDoc.missionType, missionDoc.mid)

        val expiredInstant: Instant
        val joinEndInstant: Instant
        val redeemEndInstant: Instant
        if (missionDoc.isUtcBasedMission()) {
            expiredInstant = stringToLocalDateTime(missionDoc.expiredDate).toInstant(ZoneOffset.UTC)
            joinEndInstant = stringToLocalDateTime(missionDoc.joinEndDate).toInstant(ZoneOffset.UTC)
            redeemEndInstant = stringToLocalDateTime(missionDoc.redeemEndDate).toInstant(ZoneOffset.UTC)
        } else {
            expiredInstant = stringToLocalDateTime(missionDoc.expiredDate).atZone(zone).toInstant()
            joinEndInstant = stringToLocalDateTime(missionDoc.joinEndDate).atZone(zone).toInstant()
            redeemEndInstant = stringToLocalDateTime(missionDoc.redeemEndDate).atZone(zone).toInstant()
        }


        val rewardExpiredDate = joinDoc?.rewardDocId?.let { rewardDocId ->
            rewardRepository.getRewardExpiredDate(missionDoc.rewardType, rewardDocId)
        } ?: Long.MIN_VALUE

        return MissionListItem(
                mid = missionDoc.mid,
                title = name,
                description = description,
                joinEndpoint = "/api/v1/missions/${missionDoc.missionType}/${missionDoc.mid}",
                redeemEndpoint = "/api/v1/redeem/${missionDoc.missionType}?mid=${missionDoc.mid}",
                events = missionDoc.interestPings,
                expiredDate = expiredInstant.toEpochMilli(),
                redeemEndDate = redeemEndInstant.toEpochMilli(),
                status = joinStatus,
                minVersion = missionDoc.minVersion,
                progress = progress?.toProgressResponse() ?: emptyMap(),
                important = important,
                missionType = missionDoc.missionType,
                joinEndDate = joinEndInstant.toEpochMilli(),
                imageUrl = missionDoc.imageUrl,
                rewardExpiredDate = rewardExpiredDate,
                parameters = mapOf("totalDays" to (missionDoc.missionParams["totalDays"]
                        ?: 0)) // daily mission will only expose one key: totalDays
        )
    }

    /**
     * Get localized string by string id
     * @param id string id
     * @return localized string (if any)
     */
    private fun getStringById(id: String, locale: Locale): String {
        return missionMessageSource.getMessageOrEmpty(id, locale)
    }

    fun createMissions(missionList: List<MissionCreateData>): MissionCreateResult {
        val invalidItems = missionList
                .map { it.missionName to validateMissionCreateData(it) }
                .filterNot { it.second.isValid() }
                .map { MissionCreateFailedItem(it.first, it.second.toString()) }

        if (invalidItems.isNotEmpty()) {
            return MissionCreateResult.Error(invalidItems)
        }

        val results = missionList.map { createData ->
            val mission = missionRepository.createMission(createData)
            MissionCreateResultItem(
                    mid = mission.mid,
                    title = mission.titleId,
                    description = mission.descriptionId,
                    startDate = mission.startDate,
                    joinStartDate = mission.joinStartDate,
                    joinEndDate = mission.joinEndDate,
                    expiredDate = mission.expiredDate,
                    redeemEndDate = mission.redeemEndDate,
                    events = mission.interestPings,
                    endpoint = mission.endpoint,
                    minVersion = mission.minVersion,
                    missionParams = mission.missionParams,
                    rewardType = mission.rewardType,
                    joinQuota = mission.joinQuota,
                    imageUrl = mission.imageUrl
            )
        }

        return MissionCreateResult.Success(results)
    }

    private fun validateMissionCreateData(data: MissionCreateData): ValidationResult {
        val result = ValidationResult()

        val type = MissionType.from(data.missionType)
        if (type == MissionType.Unknown) {
            result.addError("unrecognized mission type ${data.missionType}")
        }

        if (data.pings.isEmpty()) {
            result.addError("empty pings")
        }

        if (data.missionParams?.isEmpty() != false) {
            result.addError("no mission parameter specified")
        }

        return result
    }

    fun groupMissions(
            groupId: String,
            groupItems: List<MissionGroupItemData>
    ): List<MissionReferenceDoc> {
        return missionRepository.groupMissions(groupId, groupItems)
    }

    /**
     * Join user to the mission
     * @param uid user id
     * @param mid mission id
     * @return updated mission json for client
     */
    fun joinMission(
            uid: String,
            missionType: String,
            mid: String,
            zone: ZoneId,
            locale: Locale
    ): MissionJoinResult {
        val logInfo = "joinMission: uid=$uid, type=$missionType, mid=$mid, zone=$zone"

        val mission = missionRepository.findMission(missionType, mid) ?: run {
            log.info("joinMission: mission not found")
            return MissionJoinResult.Error("mission not found", HttpStatus.NOT_FOUND, JoinFailedReason.NotExist)
        }

        val joinStatus = missionRepository.getJoinStatus(uid, missionType, mid)
        val joinCount = missionRepository.getJoinCount(missionType, mid)

        when (checkJoinable(mission, joinStatus, joinCount, clock, zone)) {
            MissionJoinableState.NotOpen -> {
                log.info("joinMission: not open for join")
                return MissionJoinResult.Error("mission not open", HttpStatus.NOT_FOUND, JoinFailedReason.NotOpen)
            }

            MissionJoinableState.Closed -> {
                log.info("joinMission: exceed join period")
                return MissionJoinResult.Error("mission closed", HttpStatus.NOT_FOUND, JoinFailedReason.Closed)
            }

            MissionJoinableState.AlreadyJoin -> {
                log.info("joinMission: already joined")
                return MissionJoinResult.Error("already joined", HttpStatus.CONFLICT, JoinFailedReason.AlreadyJoined)
            }

            MissionJoinableState.NoQuota -> {
                log.info("joinMission: mission join quota reached")
                return MissionJoinResult.Error("mission reach join quota", HttpStatus.FORBIDDEN, JoinFailedReason.NoQuota)
            }

            MissionJoinableState.Unknown -> {
                log.error("joinMission: unknown error")
                return MissionJoinResult.Error("unknown error", HttpStatus.INTERNAL_SERVER_ERROR, JoinFailedReason.Unknown)
            }

            MissionJoinableState.Joinable -> {
                val joinResult = missionRepository.joinMission(uid, missionType, mid)
                missionQualifier.updateProgress(uid, mid, MissionType.from(missionType), zone, locale)
                log.info("join mission, $logInfo, state=${joinResult.status}")
                return MissionJoinResult.Success(joinResult.mid, joinResult.status)
            }
        }
    }

    internal fun checkJoinable(
            mission: MissionDoc,
            joinStatus: JoinStatus?,
            joinCount: Int,
            clock: Clock,
            zone: ZoneId
    ): MissionJoinableState {
        val missionState = try {
            getMissionJoinState(mission, joinStatus, joinCount, clock, zone)

        } catch (e: DateTimeParseException) {
            return MissionJoinableState.Unknown
        }

        if (missionState.reachQuota) {
            return MissionJoinableState.NoQuota
        }

        if (missionState.isBeforeJoinPeriod) {
            return MissionJoinableState.NotOpen
        }

        if (missionState.isAfterJoinPeriod) {
            return MissionJoinableState.Closed
        }

        if (missionState.isJoined) {
            return MissionJoinableState.AlreadyJoin
        }

        return MissionJoinableState.Joinable
    }

    internal data class MissionJoinState(
            var reachQuota: Boolean = false,

            var isBeforeJoinPeriod: Boolean = false,
            var isOpenedForJoin: Boolean = false,
            var isAfterJoinPeriod: Boolean = false,

            var isExpired: Boolean = false,
            var isJoined: Boolean = false,
            var isComplete: Boolean = false
    )

    internal sealed class MissionJoinableState {
        object Joinable : MissionJoinableState()
        object NotOpen : MissionJoinableState()
        object Closed : MissionJoinableState()
        object AlreadyJoin : MissionJoinableState()
        object NoQuota : MissionJoinableState()
        object Unknown : MissionJoinableState()
    }

    internal fun getMissionJoinState(
            mission: MissionDoc,
            joinStatus: JoinStatus?,
            joinCount: Int,
            clock: Clock,
            zone: ZoneId
    ): MissionJoinState {

        val missionState = MissionJoinState()

        missionState.reachQuota = joinCount >= mission.joinQuota

        missionState.isBeforeJoinPeriod = mission.isBeforeJoinPeriod(clock, zone)
        missionState.isOpenedForJoin = mission.isJoinPeriod(clock, zone)
        missionState.isAfterJoinPeriod = mission.isAfterJoinPeriod(clock, zone)

        missionState.isExpired = mission.isExpired(clock, zone)

        missionState.isJoined = joinStatus?.let { it != JoinStatus.New } ?: false

        missionState.isComplete = joinStatus == JoinStatus.Complete
                || joinStatus == JoinStatus.Redeemed

        return missionState
    }

    fun quitMission(
            uid: String,
            missionType: String,
            mid: String
    ): MissionQuitResponse {

        val status = missionRepository.getJoinStatus(uid, missionType, mid)

        log.info("quit mission $status")

        status ?: return MissionQuitResponse.Error("mission not exist", HttpStatus.NOT_FOUND)

        if (status != JoinStatus.Joined) {
            return MissionQuitResponse.Error("not joined", HttpStatus.CONFLICT)
        }

        missionRepository.quitMission(uid, missionType, mid)
        missionQualifier.clearProgress(uid, mid, MissionType.from(missionType))
        return MissionQuitResponse.Success(mid = mid, status = JoinStatus.New)
    }

    /**
     * Check missions interest to the given ping
     */
    fun checkInMissions(
            uid: String,
            ping: String,
            zone: ZoneId,
            locale: Locale
    ): List<MissionListItem> {

        val missions = missionRepository.findJoinedMissionsByPing(uid, ping)
        log.info("ping=$ping, missions=${missions.map { "${it.missionType}/${it.mid}" }}")

        val joinedMissions = missions.filter { isJoined(uid, it.missionType, it.mid) }

        val joinedMissionListItem = joinedMissions.map { aggregateMissionListItem(uid, it, zone, locale) }

        // update MissionProgressDoc since we only get the message when we update the progress
        // This is ugly but I don't have time to re-write `aggregateMissionListItem` right now.
        // TODO: fix the n*n complexity here :(
        joinedMissions.map {
            Metrics.event(Metrics.EVENT_MISSION_CHECK_IN, "mid:${it.mid}")
            val progressDoc = updateProgress(uid, it.missionType, it.mid, zone, locale)
            for (missionListItem in joinedMissionListItem) {
                if (missionListItem.mid == progressDoc?.mid) {
                    missionListItem.progress = progressDoc.toProgressResponse()
                }
            }
        }
        return joinedMissionListItem
    }

    private fun isJoined(uid: String, missionType: String, mid: String): Boolean {
        return missionRepository.getJoinStatus(uid, missionType, mid) == JoinStatus.Joined
    }

    private fun updateProgress(
            uid: String,
            missionType: String,
            mid: String,
            zone: ZoneId,
            locale: Locale
    ): MissionProgressDoc? {

        log.info("update progress, mid=$mid, type=$missionType")
        return missionQualifier.updateProgress(uid, mid, MissionType.from(missionType), zone, locale)
    }
}
