package org.mozilla.msrp.platform.common.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import io.opencensus.common.Scope;
import io.opencensus.trace.Tracer;
import io.opencensus.trace.Tracing;
import lombok.extern.log4j.Log4j2;
import org.mozilla.msrp.platform.common.ErrorMessage;
import org.mozilla.msrp.platform.user.UserRepository;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Named;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Date;
import java.util.Locale;

@Log4j2
@Named
public class FirebaseAuthInterceptor implements HandlerInterceptor {

    private static final String HEADER_BEAR = "Bearer ";
    private static final Tracer tracer = Tracing.getTracer();

    @Inject
    ObjectMapper mapper;

    @Inject
    UserRepository userRepository;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        try (Scope ss = tracer.spanBuilder("preHandle").startScopedSpan()) {
            tracer.getCurrentSpan().addAnnotation("preHandle -----start");


            String authorization = request.getHeader("Authorization");

            if (authorization != null && authorization.contains(HEADER_BEAR)) {

                String jwt = authorization.replace(HEADER_BEAR, "");

                try {
                    FirebaseToken decodedToken = FirebaseAuth.getInstance().verifyIdToken(jwt);

                    if (decodedToken.getUid().isEmpty()) {
                        log.warn("preHandle: decodedToken.getUid().isEmpty()");

                        handleThrowable(response, HttpStatus.UNAUTHORIZED, "No such user");

                        return false;

                    } else {
                        String userId = getUserId(decodedToken);
                        if (userId == null) {
                            log.info("preHandle: createAnonymousUser");
                            // the user document is not ready. Let's create it now.
                            userId = userRepository.createAnonymousUser(decodedToken.getUid());
                            log.info("preHandle: createAnonymousUser done:" + userId);
                        } else if (userRepository.isUserSuspended(userId)) {
                            log.warn("preHandle: user suspended uid={}", userId);
                            handleThrowable(response, HttpStatus.FORBIDDEN, "user suspended");
                            return false;
                        }

                        log.info("preHandle: success:" + userId);

                        Locale locale = request.getLocale();
                        request.setAttribute("uid", userId);
                        request.setAttribute("locale", locale);
                        log.info("preHandle: setAttribute(uid={}, locale={})", userId, locale);

                        return true;
                    }

                } catch (IllegalArgumentException e) {
                    logThrowable("illegal token format", e);
                    handleThrowable(response, HttpStatus.BAD_REQUEST, "Illegal token format");
                    return false;

                } catch (FirebaseAuthException e) {
                    logThrowable("unauthorized token", e);
                    handleThrowable(response, HttpStatus.UNAUTHORIZED, "Unauthorized token");
                    return false;

                } catch (Throwable throwable) {
                    logThrowable("unexpected exception", throwable);
                    handleThrowable(response, HttpStatus.INTERNAL_SERVER_ERROR, "Error loading DB");

                    return false;
                } finally {
                    tracer.getCurrentSpan().addAnnotation("preHandle -----end");
                }

            } else {

                log.info("abnormal access to endpoint: {}", request.getRequestURI());

                handleThrowable(response, HttpStatus.UNAUTHORIZED, "Please login first");

                tracer.getCurrentSpan().addAnnotation("preHandle -----end");
                return false;
            }

        }
    }

    private void handleThrowable(HttpServletResponse response, HttpStatus httpStatus, String message) throws IOException {
        ErrorMessage errorObj = new ErrorMessage(new Date(), message);
        String jsonMessage = mapper.writeValueAsString(errorObj);
        response.addHeader(HttpHeaders.CONTENT_TYPE, MimeTypeUtils.APPLICATION_JSON_VALUE);
        response.setStatus(httpStatus.value());
        PrintWriter writer = response.getWriter();
        writer.write(jsonMessage);
        writer.flush();
        writer.close();
    }

    @Nullable
    private String getUserId(FirebaseToken token) {
        String fbuid = token.getUid();
        String fxuid = (String) token.getClaims().getOrDefault("fxuid", "");
        return userRepository.findUserId(fbuid, fxuid);
    }

    private void logThrowable(String msg, Throwable throwable) {
        String message = NestedExceptionUtils.buildMessage(msg, throwable);
        log.info("authenticate failed, msg={}", message);
    }
}