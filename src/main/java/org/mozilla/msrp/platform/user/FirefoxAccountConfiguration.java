package org.mozilla.msrp.platform.user;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.QuerySnapshot;
import io.opencensus.common.Scope;
import io.opencensus.trace.Tracer;
import io.opencensus.trace.Tracing;
import lombok.extern.log4j.Log4j2;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;

import java.util.List;
import java.util.concurrent.ExecutionException;

@Log4j2
@Configuration
public class FirefoxAccountConfiguration {
    private static final Tracer tracer = Tracing.getTracer();

    @Bean
    @DependsOn({"Firestore"})
    public FirefoxAccountServiceInfo firefoxAccountServiceInfo(Firestore firestore) {
        try (Scope ss = tracer.spanBuilder("FirefoxAccountServiceInfo").startScopedSpan()) {
            tracer.getCurrentSpan().addAnnotation("FirefoxAccountServiceInfo -----start");
            log.info(" --- Bean Creation firefoxAccountServiceInfo ---");
            try {
                // asynchronously retrieve all users
                ApiFuture<QuerySnapshot> query = firestore.collection("settings").get();
                QuerySnapshot querySnapshot = query.get();
                List<QueryDocumentSnapshot> documents = querySnapshot.getDocuments();
                for (QueryDocumentSnapshot document : documents) {

                    String id = document.getString("fxa_client_id");
                    String secret = document.getString("fxa_client_secret");
                    String token = document.getString("fxa_api_token");
                    String profile = document.getString("fxa_api_profile");

                    log.info("Get FirefoxAccount settings --- success ---:" + id);
                    tracer.getCurrentSpan().addAnnotation("FirefoxAccountServiceInfo -----end ");

                    return new FirefoxAccountServiceInfo(id, secret, token, profile);
                }
            } catch (InterruptedException | ExecutionException e) {
                log.error("Get FirefoxAccount settings -- failed :" + e);
            }
            log.error("Get FirefoxAccount settings -- failed, shouldn't reach this line --- ");
            tracer.getCurrentSpan().addAnnotation("FirefoxAccountServiceInfo -----end error");
        }
        return null;
    }
}
