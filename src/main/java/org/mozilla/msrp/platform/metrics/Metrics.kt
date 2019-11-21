package org.mozilla.msrp.platform.metrics

import org.mozilla.msrp.platform.util.logger

class Metrics {

    companion object {
        const val EVENT_USER_SUSPENDED = "user_suspended"
        const val EVENT_USER_BIND_FAIL = "user_bind_fail"
        const val EVENT_REDEEM_FAIL = "redeem_fail"
        const val EVENT_REDEEM_CONSUMED = "redeem_consumed"
        const val EVENT_MISSION_JOINED = "mission_joined"
        const val EVENT_MISSION_CHECK_IN = "mission_check_in"

        @JvmStatic
        fun event(key: String, value: String = "") {
            logger().info("$key==$value")
        }
    }
}