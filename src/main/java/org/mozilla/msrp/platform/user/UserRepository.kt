package org.mozilla.msrp.platform.user

import com.google.cloud.firestore.CollectionReference
import com.google.cloud.firestore.Firestore
import com.google.cloud.firestore.QueryDocumentSnapshot
import com.google.cloud.firestore.SetOptions
import com.google.firebase.auth.FirebaseAuth
import org.mozilla.msrp.platform.firestore.getResultsUnchecked
import org.mozilla.msrp.platform.firestore.getUnchecked
import org.mozilla.msrp.platform.firestore.setUnchecked
import org.mozilla.msrp.platform.metrics.Metrics
import org.mozilla.msrp.platform.user.data.UserActivityDoc
import org.mozilla.msrp.platform.user.data.UserDoc
import org.mozilla.msrp.platform.util.logger
import org.springframework.stereotype.Repository
import java.time.Clock
import javax.inject.Inject

@Repository
class UserRepository @Inject constructor(firestore: Firestore) {

    private var users: CollectionReference
    private var publishAdmin: CollectionReference
    private var userActivity: CollectionReference
    private val logger = logger()

    @Inject
    lateinit var clock: Clock


    companion object {
        private const val COLLECTION_USER = "users"
        private const val COLLECTION_USER_ACTIVITY = "user_activity"

        private const val COLLECTION_PUBLISH_ADMIN = "publish_admin"

        private const val USER_SUSPEND_THRESHOLD = 3
        private const val USER_SECOND_WARNING = 2
        private const val USER_FIRST_WARNING = 1
    }

    init {
        users = firestore.collection(COLLECTION_USER)
        userActivity = firestore.collection(COLLECTION_USER_ACTIVITY)

        publishAdmin = firestore.collection(COLLECTION_PUBLISH_ADMIN)

    }

    fun createCustomToken(fxUid: String, additionalClaims: Map<String, String>): String? {
        return FirebaseAuth.getInstance().createCustomToken(fxUid, additionalClaims)
    }

    fun signInAndUpdateUserDocument(oldFbUid: String, fxUid: String, email: String): LoginResponse {

        val userDocIdFb = findUserDocumentIdByFbUid(oldFbUid)
        if ((userDocIdFb == null)) {
            logger.error("No such user $oldFbUid in User Document")
            return LoginResponse.Fail("No such user $oldFbUid")
        }

        val userDocIdFxA = findUserDocumentIdByFxUid(fxUid)
        logger.info("signInAndUpdateUserDocument=== userDocIdFb[$userDocIdFb]====userDocIdFxA[$userDocIdFxA]")

        // the same FxA is used to login FxA
        if (userDocIdFxA != null) {

            logger.info("userDocIdFxA != null")

            // if the user is deprecated, fail fast
            val userDocFxA = findUserDocumentByFxUid(fxUid)
            if (userDocFxA?.status == UserDoc.STATUS_SUSPEND) {
                logger.info("UserDoc[$userDocIdFxA] is ${UserDoc.STATUS_SUSPEND}")
                logUserActivity(userDocIdFxA, UserDoc.STATUS_SUSPEND)   // suspended user logs in again and is still suspended.
                return LoginResponse.UserSuspended("UserDoc[$userDocIdFxA] is ${UserDoc.STATUS_SUSPEND}")
            }
            // get the sign-in count for the last 7 days
            val signInCountLast7DAYS: Int = signInCountLast7DAYS(userDocIdFxA)
            logger.info("signInCountLast7DAYS [$signInCountLast7DAYS]")

            // the user had two records in the past week. Means this time is the third time.
            // we should now suspend the user.
            if (USER_SUSPEND_THRESHOLD == signInCountLast7DAYS) {
                setUserDocStatus(userDocIdFxA, UserDoc.STATUS_SUSPEND)
                logUserActivity(userDocIdFxA, UserDoc.STATUS_SUSPEND)
                Metrics.event(Metrics.EVENT_USER_SUSPENDED)
                logger.info("UserDoc[$userDocIdFxA] has logged in three times per 7 days.")
                return LoginResponse.UserSuspended("UserDoc[$userDocIdFxA] has logged in three times per 7 days.")
            }


            // the user document is the same, means the user sign in FxA in the same device.
            if (userDocIdFxA == userDocIdFb) {
                logger.info("userDocIdFxA == userDocIdFb")

                logUserActivity(userDocIdFxA, UserDoc.STATUS_SIGN_IN)

            } else {
                logger.info("signIn userDocIdFxA[$userDocIdFxA]")
                // the user document is different, means the user sign in FxA in another device.
                setUserDocStatus(userDocIdFxA, UserDoc.STATUS_SIGN_IN)
                logUserActivity(userDocIdFxA, UserDoc.STATUS_SIGN_IN)

                logger.info("deprecate userDocIdFb[$userDocIdFb]")
                // set the old document deprecated
                setUserDocStatus(userDocIdFb, UserDoc.STATUS_DEPRECATED)
                logUserActivity(userDocIdFb, UserDoc.STATUS_DEPRECATED)

            }

            // the user had one record in the past week. Means this time is the second time.
            // we should warn the user instead of returning success
            if (USER_FIRST_WARNING == signInCountLast7DAYS) {
                logger.warn("UserDoc[$userDocIdFxA] has two more chances this week")
                return LoginResponse.FirstWarning("You have logged in twice this week.")
            }
            if (USER_SECOND_WARNING == signInCountLast7DAYS) {
                logger.warn("UserDoc[$userDocIdFxA] has one more chance this week")
                return LoginResponse.SecondWarning("You have logged in three times this week.")
            }
            logger.info("UserDoc[$userDocIdFxA] has logged in Successfully")
            return LoginResponse.Success("UserDoc[$userDocIdFxA] has sign in to FxAcc")
        } else {
            logger.info("userDocIdFxA == null")

            val updateData = mapOf(
                    UserDoc.KEY_FIREFOX_UID to fxUid,
                    UserDoc.KEY_EMAIL to email,
                    UserDoc.KEY_UPDATED_TIMESTAMP to clock.millis(),
                    UserDoc.KEY_STATUS to UserDoc.STATUS_SIGN_IN
            )
            users.document(userDocIdFb).set(updateData, SetOptions.merge())

            // add account activity
            logUserActivity(userDocIdFb, UserDoc.STATUS_SIGN_IN)

            logger.info("UserDoc is promoted [$userDocIdFxA] just logged in")
            return LoginResponse.Success("UserDoc is promoted [$userDocIdFxA] just logged in")
        }
    }

    private fun setUserDocStatus(currentUserDocId: String, status: String) {
        users.document(currentUserDocId).set(
                mapOf(UserDoc.KEY_STATUS to status,
                        UserDoc.KEY_UPDATED_TIMESTAMP to clock.millis()), SetOptions.merge())
    }


    private fun signInCountLast7DAYS(existingUserDocId: String): Int {
        val now = clock.millis()
        val aWeekAgo = now - 7 * 24 * 60 * 60 * 1000L
        return userActivity.whereEqualTo(UserActivityDoc.KEY_USER_DOC_ID, existingUserDocId)
                .whereEqualTo(UserDoc.KEY_STATUS, UserDoc.STATUS_SIGN_IN)
                .whereGreaterThan(UserDoc.KEY_UPDATED_TIMESTAMP, aWeekAgo)
                .getResultsUnchecked().size
    }

    fun findUserId(fbuid: String, fxuid: String): String? {
        return if (fxuid.isEmpty()) {
            findUserIdByFbUid(fbuid)
        } else {
            findUserIdByFxUid(fxuid)
        }
    }

    private fun findUserIdByFxUid(fxUid: String): String? {
        return users.whereEqualTo(UserDoc.KEY_FIREFOX_UID, fxUid)
                .getResultsUnchecked()
                .firstOrNull()
                ?.getString(UserDoc.KEY_UID)
    }

    private fun findUserIdByFbUid(fbUid: String): String? {
        return users.whereEqualTo(UserDoc.KEY_FIREBASE_UID, fbUid)
                .getResultsUnchecked()
                .firstOrNull()
                ?.getString(UserDoc.KEY_UID)
    }

    private fun findUserDocumentIdByFbUid(fbUid: String): String? {
        return findUserDocSnapshot(UserDoc.KEY_FIREBASE_UID, fbUid)?.id
    }

    private fun findUserDocumentIdByFxUid(fxUid: String): String? {
        return findUserDocSnapshot(UserDoc.KEY_FIREFOX_UID, fxUid)?.id
    }

    private fun findUserDocumentByFxUid(fxUid: String): UserDoc? {
        return findUserDocSnapshot(UserDoc.KEY_FIREFOX_UID, fxUid)?.toObject(UserDoc::class.java)
    }

    private fun findUserDocSnapshot(field: String, fxUid: String): QueryDocumentSnapshot? {
        return users.whereEqualTo(field, fxUid).getResultsUnchecked().getOrNull(0)
    }


    //.toObject(UserDoc::class.java)
    private fun logUserActivity(userDocumentId: String, action: String) {
        UserActivityDoc(userDocumentId, clock.millis(), action).let {

            userActivity.document().set(it)

            logger.info("log UserDoc[$userDocumentId] has action [$action] ")
        }
    }

    fun isPublishAdmin(email: String): Boolean {
        if (email.contains("@mozilla.com")) {
            for (document in publishAdmin.get().getUnchecked().documents) {
                if (document.getString("email") == email) {
                    return true
                }
            }
        }
        return false
    }

    fun isMsrpAdmin(email: String): Boolean {
        return isPublishAdmin(email)
    }

    fun findFirebaseUidByEmail(email: String): String? {
        if (isPublishAdmin(email)) {
            val resultsUnchecked = users.whereEqualTo("email", email).getResultsUnchecked()
            if (resultsUnchecked.size >= 1) {
                return resultsUnchecked[0].getString("firebase_uid")
            }
        }
        return null
    }

    fun isFxaUser(uid: String): Boolean {
        val fxUid = users.whereEqualTo(UserDoc.KEY_UID, uid).getResultsUnchecked().firstOrNull()?.get(UserDoc.KEY_FIREFOX_UID) as? String?
        return fxUid?.isEmpty() ?: false
    }

    fun createAnonymousUser(firebaseUid: String): String {
        val document = users.document()
        val docId = document.id
        val ts = clock.millis()
        val userDoc = UserDoc(uid = docId, firebase_uid = firebaseUid, created_timestamp = ts, updated_timestamp = ts)
        document.setUnchecked(userDoc)
        return docId
    }

    fun isUserSuspended(uid: String): Boolean {
        val status = users.whereEqualTo(UserDoc.KEY_UID, uid)
                .getResultsUnchecked()
                .firstOrNull()
                ?.get(UserDoc.KEY_STATUS) as? String?

        return status == UserDoc.STATUS_SUSPEND
    }
}

sealed class LoginResponse {
    open class Success(open val message: String) : LoginResponse()
    class FirstWarning(val message: String) : LoginResponse() // a special version of failure
    class SecondWarning(val message: String) : LoginResponse() // a special version of failure
    class UserSuspended(val message: String) : LoginResponse()  // a special version of failure
    class Fail(val message: String) : LoginResponse() // Business logic
}