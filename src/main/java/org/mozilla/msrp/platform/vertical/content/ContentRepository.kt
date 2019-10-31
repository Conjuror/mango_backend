package org.mozilla.msrp.platform.vertical.content

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.api.core.ApiFuture
import com.google.cloud.firestore.CollectionReference
import com.google.cloud.firestore.DocumentSnapshot
import com.google.cloud.firestore.Firestore
import com.google.cloud.firestore.Query
import com.google.cloud.firestore.QueryDocumentSnapshot
import com.google.cloud.storage.Storage
import com.google.cloud.storage.StorageException
import org.mozilla.msrp.platform.firestore.getResultsUnchecked
import org.mozilla.msrp.platform.firestore.getUnchecked
import org.mozilla.msrp.platform.firestore.toObject
import org.mozilla.msrp.platform.util.logger
import org.mozilla.msrp.platform.vertical.content.data.Category
import org.mozilla.msrp.platform.vertical.content.db.PublishDoc
import org.springframework.stereotype.Repository
import java.text.ParseException
import java.text.SimpleDateFormat
import java.time.Clock
import java.time.Instant
import java.util.TimeZone
import javax.inject.Inject

@Repository
class ContentRepository @Inject constructor(private var storage: Storage,
                                            private var firestore: Firestore) {

    private var publish: CollectionReference
    private var publishHistory: CollectionReference

    private val log = logger()

    @Inject
    lateinit var clock: Clock

    @Inject
    lateinit var mapper: ObjectMapper

    companion object {
        private const val COLLECTION_PUBLISH = "publish"
        private const val COLLECTION_PUBLISH_HISTORY = "publish_history"
        val simpleDateFormat = SimpleDateFormat("yyyy-MM-dd").apply {
            this.timeZone = TimeZone.getTimeZone("UTC");
        }
    }

    init {
        publish = firestore.collection(COLLECTION_PUBLISH)
        publishHistory = firestore.collection(COLLECTION_PUBLISH_HISTORY)
    }

    // This version of getContent gets content from Firestore, not Cloud Storage
    fun getContentFromDB(contentRepoQuery: ContentRepoQuery): ContentRepoResult {
        return try {

            val publishDocId: QueryDocumentSnapshot? = getLatestPublish(contentRepoQuery.category, contentRepoQuery.locale)
            if (publishDocId == null) {
                val message = "[Content][getContentFromDB]====No result for :$contentRepoQuery"
                log.warn(message)
                return ContentRepoResult.Fail(message)
            }
            val publishDoc = publishDocId.toObject(PublishDoc::class.java)
            val publishTimestamp = publishDoc.publish_timestamp
            val data = publishDoc.data
            val tag = publishDoc.tag
            if (data == null || publishTimestamp == null || tag == null) {
                val message = "[Content]====getContentFromDB====No such Document===="
                log.error("$message$publishDocId")
                ContentRepoResult.Fail(message)
            } else {
                ContentRepoResult.Success(publishTimestamp, tag, data)
            }
        } catch (e: StorageException) {
            val message = "error loading games"
            log.error("[Content]====$message:${e.localizedMessage}")
            ContentRepoResult.Fail(message)
        }
    }

    fun addContent(request: AddContentRequest): String? {
        try {
            val publishDoc = PublishDoc(
                    null,
                    request.category,
                    request.locale,
                    request.tag,
                    clock.millis(),
                    mapper.readValue(request.data, Category::class.java))
            val document = publish.document()
            document.set(publishDoc).getUnchecked()
            return document.id
        } catch (e: Exception) {
            log.error("[ContentRepository][addContent]====$e")
            return null
        }
    }

    fun publish(publishDocId: String, editor: String, schedule: String?): ContentRepositoryPublishResult {
        var errorMessage: String
        try {

            val timestamp = if (schedule == null || schedule.isEmpty()) {
                Instant.now().toEpochMilli()
            } else {
                simpleDateFormat.parse(schedule).time
            }

            val docRef = publish.document(publishDocId)
            var snapshot: ApiFuture<DocumentSnapshot>? = null
            var publishDoc: PublishDoc? = null
            firestore.runTransaction { transaction ->
                snapshot = transaction.get(docRef)
                publishDoc = snapshot?.getUnchecked()?.toObject(PublishDoc::class.java)

                transaction.update(docRef, "publish_timestamp", timestamp)
            }.getUnchecked()

            if (publishDoc == null) {
                log.error("[Content][publish]====$publishDocId/$editor/$schedule")
                return ContentRepositoryPublishResult.Fail("No content found")
            }
            if (!isValid(publishDoc)) {
                log.error("[Content][publish]====$publishDocId/$editor/$schedule")
                return ContentRepositoryPublishResult.Fail("Content is not valid")
            }
            updatePublishHistory(publishDocId, editor)

            return ContentRepositoryPublishResult.Success(publishDoc?.category ?: "", publishDoc?.locale
                    ?: "") // already checked
        } catch (e: ParseException) {
            errorMessage = "[Content][publish]====$publishDocId/$editor/$schedule/ParseException:$e"

        } catch (e: Exception) {
            errorMessage = "[Content][publish]====$publishDocId/$editor/$schedule/exception:$e"
        }
        log.error(errorMessage)
        return ContentRepositoryPublishResult.Fail(errorMessage)
    }

    private fun updatePublishHistory(publishDocId: String, editor: String) {
        publishHistory.document().set(
                mapOf("publishDocId" to publishDocId,
                        "created_timestamp" to clock.millis(),
                        "editor" to editor
                )).getUnchecked()
    }

    private fun getLatestPublish(category: String, locale: String): QueryDocumentSnapshot? {
        return publish.whereEqualTo("category", category)
                .whereEqualTo("locale", locale)
                .whereLessThan("publish_timestamp", Instant.now().toEpochMilli())
                .orderBy("publish_timestamp", Query.Direction.DESCENDING)
                .limit(1).getResultsUnchecked().firstOrNull()
    }

    fun queryPublish(contentRepoQuery: ContentRepoQuery): List<PublishDoc> {
        return try {
            val search = publish
                    .whereEqualTo("category", contentRepoQuery.category)
                    .whereEqualTo("locale", contentRepoQuery.locale)
            if (contentRepoQuery.tag != null) {
                search.whereEqualTo("tag", contentRepoQuery.tag)
            }
            search.getResultsUnchecked().mapNotNull {
                it.toObject(PublishDoc::class.java, mapper)
            }
        } catch (e: Exception) {
            log.error("[Content][error]====queryPublish====[$contentRepoQuery]====$e")
            listOf()
        }
    }

    fun getContentByPublishDocId(publishDocId: String): PublishDoc? {
        return publish.document(publishDocId).getUnchecked().toObject(PublishDoc::class.java)
    }
    /**
    publish/{publishID}/
    publish_date{Long, null if not ready for publish})
    type{coupon/deals}
    locale{id-ID/en-IN}
    data:
    /*JSON for client*/
    publishHistory/{historyId}
    publishDocId
    publish Date
    editor
    publishControl       // only one document.
    v1/content_shopping/
     */
}

fun isValid(publish: PublishDoc?): Boolean {
    if (publish == null || publish.category.isNullOrBlank() ||
            publish.locale.isNullOrBlank() ||
            publish.created_timestamp == null ||
            publish.data == null
    ) {
        return false
    }
    return true
}

class AddContentRequest(
        val tag: String,
        val category: String,
        val locale: String,
        val data: String,
        var publishDate: Long? = null
)


sealed class ContentRepoResult {
    class Success(val version: Long, val tag: String, val data: Category) : ContentRepoResult()
    class Fail(val message: String) : ContentRepoResult()
}

data class ContentRepoQuery(
        val category: String,
        val locale: String,
        var tag: String? = null
)

sealed class ContentRepositoryPublishResult {
    class Success(val category: String, val locale: String) : ContentRepositoryPublishResult()
    class Fail(val message: String) : ContentRepositoryPublishResult()
}
