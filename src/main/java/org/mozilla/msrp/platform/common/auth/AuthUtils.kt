package org.mozilla.msrp.platform.common.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.JWTCreationException
import com.auth0.jwt.exceptions.JWTVerificationException
import org.mozilla.msrp.platform.user.FirefoxAccountServiceInfo
import org.mozilla.msrp.platform.util.logger
import java.util.Date
import javax.inject.Inject
import javax.inject.Named

@Named
class JwtHelper @Inject constructor(firefoxAccountServiceInfo: FirefoxAccountServiceInfo) {

    private val SECRET = firefoxAccountServiceInfo.clientSecret   // the secret must be the same among all instances
    private val algorithm = Algorithm.HMAC256(SECRET)
    private val verifier = JWT.require(algorithm).withIssuer(ISSUER).build()
    private val log = logger()

    companion object{
        const val ROLE_PUBLISH_ADMIN = "publish_admin"
        const val ROLE_MSRP_ADMIN = "msrp_admin"

        private const val ISSUER = "org.mozilla.msrp"
        private const val ROLE = "role"
        private const val EMAIL = "email"
        private const val EXPIRATION = 30 * 60 * 1000  // expired in 50 minutes
    }

    fun createToken(role: String, email: String): String? {
        return try {
            log.info("Admin login createToken request")

            val algorithm = Algorithm.HMAC256(SECRET)
            JWT.create()
                    .withExpiresAt(Date(System.currentTimeMillis() + EXPIRATION))
                    .withIssuer(ISSUER)
                    .withClaim(ROLE, role)
                    .withClaim(EMAIL, email)
                    .sign(algorithm)
        } catch (exception: JWTCreationException) {
            log.error("[JwtHelper][createToken]====$exception")
            null

        }
    }

    fun verify(token: String): Auth? {
        log.info("Admin login verify request")
        return try {
            return verifier.verify(token).let {
                Auth(it.getClaim(ROLE).asString(), it.getClaim(EMAIL).asString())
            }
        } catch (exception: JWTVerificationException) {
            log.error("[JwtHelper][verify]====$exception")
            null
        }
    }
}

class Auth(
        val role: String,
        val email: String
)