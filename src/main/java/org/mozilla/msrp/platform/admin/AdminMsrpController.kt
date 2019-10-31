package org.mozilla.msrp.platform.admin

import org.mozilla.msrp.platform.common.auth.JwtHelper
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import javax.inject.Inject


@Controller
class AdminMsrpController @Inject constructor(private val jwtHelper: JwtHelper) {

    @GetMapping("/api/v1/admin/msrp")
    fun adminMSRP(@RequestParam token: String, model: Model): String {
        val role = jwtHelper.verify(token)?.role
        if (role == JwtHelper.ROLE_MSRP_ADMIN) {
            model.addAttribute("token", token)
            return "msrp"
        }
        return "401"
    }
}
