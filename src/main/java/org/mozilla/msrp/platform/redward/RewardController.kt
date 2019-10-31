package org.mozilla.msrp.platform.redward

import org.mozilla.msrp.platform.common.auth.JwtHelper
import org.mozilla.msrp.platform.util.logger
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestAttribute
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.time.DateTimeException
import java.time.ZoneId
import javax.inject.Inject


sealed class RedeemResponse {
    class Success(val rewardCoupon: RewardCouponDoc) : RedeemResponse()
    class Fail(val message: String) : RedeemResponse()
}

sealed class CouponUploadResponse {
    class Success(val message: String) : CouponUploadResponse()
    class Fail(val message: String) : CouponUploadResponse()
}

/**
 * Reward related API
 *
 * */
@RestController
class RedeemController @Inject constructor(val rewardRepository: RewardRepository) {

    private var logger = logger()

    @Inject
    private lateinit var jwtHelper: JwtHelper

    /**
     * Get redeem code if possible.
     * Return the failing reason from the service.
     *
     * */
    @RequestMapping("/api/v1/redeem/{missionType}")
    internal fun redeem(@PathVariable("missionType") missionType: String,
                        @RequestParam(value = "tz") tz: String,
                        @RequestParam(value = "mid") mid: String,
                        @RequestAttribute("uid") uid: String): ResponseEntity<RedeemResponse>? {
        val zoneId = createZone(tz)
                ?: return ResponseEntity(RedeemResponse.Fail("Timezone is not correct"), HttpStatus.BAD_REQUEST)

        return when (val redeemResult = rewardRepository.redeem(missionType, mid, uid, zoneId)) {
            is RedeemResult.Success -> {
                logger.info(redeemResult.debugInfo)
                ResponseEntity(RedeemResponse.Success(redeemResult.rewardCouponDoc), HttpStatus.OK)
            }
            is RedeemResult.UsedUp -> {
                logger.info(redeemResult.debugInfo)
                ResponseEntity(RedeemResponse.Fail(redeemResult.message), HttpStatus.NOT_FOUND)
            }
            is RedeemResult.NotReady -> {
                logger.info(redeemResult.debugInfo)
                ResponseEntity(RedeemResponse.Fail(redeemResult.message), HttpStatus.FORBIDDEN)
            }
            is RedeemResult.InvalidReward -> {
                logger.info(redeemResult.debugInfo)
                ResponseEntity(RedeemResponse.Fail(redeemResult.message), HttpStatus.BAD_REQUEST)
            }
            is RedeemResult.Failure -> {
                logger.error(redeemResult.debugInfo)
                ResponseEntity(RedeemResponse.Fail(redeemResult.message), HttpStatus.INTERNAL_SERVER_ERROR)
            }
        }
    }

    private fun createZone(timezone: String): ZoneId? {
        return try {
            ZoneId.of(timezone)

        } catch (e: DateTimeException) {
            logger.info("unsupported timezone=$timezone")
            null
        }
    }

    @RequestMapping(value = ["/api/v1/admin/coupon"], method = [RequestMethod.POST])
    internal fun uploadCoupons(@RequestParam token: String,
                               @RequestParam("couponName") couponName: String,
                               @RequestParam displayName:String,
                               @RequestParam openLink: String,
                               @RequestParam("file") file: MultipartFile,
                               @RequestParam("missionType") missionType: String,
                               @RequestParam("mid") mid: String,
                               @RequestParam("expiredDate") expiredDate: String,
                               @RequestParam(required = false) clear: Boolean = false): ResponseEntity<CouponUploadResponse> {
        if (jwtHelper.verify(token)?.role != JwtHelper.ROLE_MSRP_ADMIN) {
            logger.warn("[Reward][uploadCoupons] Role violation: token[$token] couponName[$couponName] missionType[$missionType] mid[$mid]")
            return ResponseEntity(CouponUploadResponse.Fail("No Permission"), HttpStatus.UNAUTHORIZED)
        }
        if (file.isEmpty) {
            logger.warn("[Reward][uploadCoupons] Empty file: token[$token] couponName[$couponName] missionType[$missionType] mid[$mid]")

            return ResponseEntity.badRequest().body(CouponUploadResponse.Fail("empty coupon file"))
        }

        val coupons = file.inputStream.bufferedReader().readLines()
        if (coupons.isEmpty()) {
            logger.warn("[Reward][uploadCoupons] No content: token[$token] couponName[$couponName] missionType[$missionType] mid[$mid]")

            return ResponseEntity.badRequest().body(CouponUploadResponse.Fail("illegal coupon file format, please " +
                    "separate coupon codes into separated lines"))
        }

        val uploadCouponsResult = rewardRepository.uploadCoupons(
                coupons = coupons,
                displayName = displayName,
                openLink = openLink,
                couponName = couponName,
                expiredDate = expiredDate,
                missionType = missionType,
                mid = mid,
                clear = clear
        )
        return when (uploadCouponsResult) {
            is UploadCouponsResult.Success -> ResponseEntity.ok(CouponUploadResponse.Success("${uploadCouponsResult.list.size} coupons uploaded"))
            UploadCouponsResult.Duplicated -> ResponseEntity(CouponUploadResponse.Fail("couponName  $couponName duplicated"), HttpStatus.CONFLICT)
            UploadCouponsResult.NoMatchingMission -> ResponseEntity(CouponUploadResponse.Fail("Mission $mid not found"), HttpStatus.NOT_FOUND)
        }
    }
}