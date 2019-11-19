package org.mozilla.msrp.platform.vertical

import com.google.cloud.firestore.Firestore
import org.mozilla.msrp.platform.util.logger
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.DependsOn
import java.util.concurrent.ExecutionException

@Configuration
open class VerticalApiConfiguration {

    private val log = logger()

    @Bean
    @DependsOn("Firestore")
    open fun provideVerticalApiInfo(firestore: Firestore): VerticalApiInfo? {
        log.info(" --- Bean Creation VerticalApiInfo ---")
        try {
            val query = firestore.collection("settings").get()
            val querySnapshot = query.get()
            val documents = querySnapshot.documents
            for (document in documents) {
                val clientApiKey = document.getString("vertical_client_api_key")
                        ?: throw IllegalStateException("Vertical client api key is not set")

                log.info("Get VerticalApiInfo settings --- success ---")
                return VerticalApiInfo(clientApiKey)
            }
        } catch (e: InterruptedException) {
            log.error("Get VerticalApiInfo settings -- failed :$e")
        } catch (e: ExecutionException) {
            log.error("Get VerticalApiInfo settings -- failed :$e")
        }

        log.error("Get VerticalApiInfo settings -- failed, shouldn't reach this line --- ")
        return null
    }
}

class VerticalApiInfo(
        val clientApiKey: String
)
