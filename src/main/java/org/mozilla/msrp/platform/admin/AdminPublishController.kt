package org.mozilla.msrp.platform.admin

import org.mozilla.msrp.platform.common.auth.JwtHelper
import org.mozilla.msrp.platform.util.logger
import org.mozilla.msrp.platform.vertical.content.ContentService
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import javax.inject.Inject


@Controller
class AdminPublishController @Inject constructor(val conteService: ContentService) {

    @Inject
    private lateinit var jwtHelper: JwtHelper
    private val log = logger()

    @GetMapping("/api/v1/admin/publish")
    fun adminPublish(
            @RequestParam token: String,
            model: Model): String {
        val role = jwtHelper.verify(token)?.role
        if (role == JwtHelper.ROLE_PUBLISH_ADMIN) {
            model.addAttribute("token", token)
            log.info("Success: adminPublish: $role")
            return "publish"
        }
        log.error("No permission: previewContent: $token")
        return "401"
    }

    @GetMapping("/api/v1/admin/publish/preview")
    internal fun previewContent(
            @RequestParam token: String,
            @RequestParam publishDocId: String,
            model: Model
    ): String {
        val role = jwtHelper.verify(token)?.role
        if (role == JwtHelper.ROLE_PUBLISH_ADMIN) {
            model.addAttribute("token", token)
            val publishDoc = conteService.getPublish(publishDocId)
            if (publishDoc != null) {
                model.addAttribute("publishDoc", publishDoc)
                log.info("Success: previewContent: $publishDocId")
                return "preview"
            } else {
                return "401"
            }
        }
        log.error("No permission: previewContent: $token")
        return "401"

    }
}
