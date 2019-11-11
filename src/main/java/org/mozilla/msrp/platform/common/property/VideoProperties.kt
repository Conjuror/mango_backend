package org.mozilla.msrp.platform.common.property

import org.mozilla.msrp.platform.util.logger
import org.springframework.boot.context.properties.ConfigurationProperties
import javax.annotation.PostConstruct
import javax.inject.Named

/**
 * Type-safe configuration properties which are setup in application.yml file.
 */
@Named
@ConfigurationProperties("video")
class VideoProperties {
    private val log = logger()

    var cacheSize: Long = 100L
    var cacheTtl: Long = 24L

    @PostConstruct
    fun printProperties() {
        log.info("VideoProperties is initialized: {}", toString())
    }
}
