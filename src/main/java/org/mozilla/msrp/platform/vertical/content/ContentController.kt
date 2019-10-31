package org.mozilla.msrp.platform.vertical.content

import com.fasterxml.jackson.databind.ObjectMapper
import org.mozilla.msrp.platform.common.auth.JwtHelper
import org.mozilla.msrp.platform.util.logger
import org.mozilla.msrp.platform.vertical.content.data.ContentSubcategory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import javax.inject.Inject
import javax.servlet.http.HttpServletResponse


@RestController
class ContentController @Inject constructor(private val contentService: ContentService) {

    private val log = logger()
    @Inject
    lateinit var mapper: ObjectMapper

    @Inject
    private lateinit var jwtHelper: JwtHelper

    @RequestMapping("/api/v1/content")
    fun getContent(
            @RequestParam(value = "category") category: String,
            @RequestParam(value = "locale") locale: String
    ): ResponseEntity<Any> {
        return when (val result = contentService.getContent(category, locale)) {
            is ContentServiceQueryResult.InvalidParam -> ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result.message)
            is ContentServiceQueryResult.Success -> ResponseEntity.status(HttpStatus.OK).body(ContentResponse(result.version, result.tag, result.data.subcategories))
            is ContentServiceQueryResult.Fail -> ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result.message)
        }
    }

    // ======================== ADMIN ======================== START
    @GetMapping("/api/v1/content/publish")
    fun publishContent(
            @RequestParam token: String,
            @RequestParam publishDocId: String,
            @RequestParam editor: String,
            @RequestParam(required = false) schedule: String? = ""
    ): ResponseEntity<String> {
        if (jwtHelper.verify(token)?.role != JwtHelper.ROLE_PUBLISH_ADMIN) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("No Permission")
        }
        return when (val result = contentService.publish(publishDocId, editor, schedule)) {
            is ContentServicePublishResult.Success -> ResponseEntity.status(HttpStatus.OK).body("<a href='../content?category=${result.category}&locale=${result.locale}'>View Client JSON</a>")
            is ContentServicePublishResult.Fail -> ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result.message)
        }
    }


    @RequestMapping(value = ["/api/v1/admin/content"], method = [RequestMethod.POST])
    internal fun uploadContent(
            @RequestParam token: String,
            @RequestParam(value = "category") category: String,
            @RequestParam(value = "locale") locale: String,
            @RequestParam(value = "tag") tag: String,
            @RequestParam(value = "banner", required = false) banner: MultipartFile?,
            @RequestParam(value = "other") other: MultipartFile,
            response: HttpServletResponse  // need HttpServletResponse to redirect
    ): ResponseEntity<String> {
        val verify = jwtHelper.verify(token)
        if (verify?.role != JwtHelper.ROLE_PUBLISH_ADMIN) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("No Permission")
        }
        return when (val result = contentService.uploadContent(category, locale, other, banner, tag)) {
            is ContentServiceUploadResult.Success -> {
                val preview = "/api/v1/admin/publish/preview?token=$token&publishDocId=${result.publishDocId}"
                val publish = "/api/v1/content/publish?token=$token&category=$category&locale=$locale&publishDocId=${result.publishDocId}&editor=${verify.email}"
                ResponseEntity.status(HttpStatus.OK).body("" +
                        "<a href='$preview'>Preview</a> <BR> " +
                        "<a href='$publish'>Publish Now</a><BR> " +
                        "<form action=$publish>" +
                        "<input type='hidden' name='token' value='$token'>" +
                        "<input type='hidden' name='category' value='$category'>" +
                        "<input type='hidden' name='locale' value='$locale'>" +
                        "<input type='hidden' name='publishDocId' value='${result.publishDocId}'>" +
                        "<input type='hidden' name='editor' value='${verify.email}'>" +

                        "<input type='date' name='schedule'>" +
                        "<input type='submit' value='Schedule Publish'>" +
                        "</form>")
            }
            is ContentServiceUploadResult.InvalidParam -> ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result.message)
            is ContentServiceUploadResult.Fail -> ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result.message)
        }
    }


    // ======================== ADMIN ======================== END

}

class ContentResponse(
        val version: Long,
        val tag: String,
        val subcategories: List<ContentSubcategory>
)