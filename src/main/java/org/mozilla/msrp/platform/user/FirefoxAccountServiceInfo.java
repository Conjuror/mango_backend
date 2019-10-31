package org.mozilla.msrp.platform.user;


public class FirefoxAccountServiceInfo {
    private final String clientId;
    private final String clientSecret;
    private final String apiToken;
    private final String apiProfile;

    public FirefoxAccountServiceInfo(String clientId, String clientSecret, String apiToken, String apiProfile) throws IllegalStateException {
        if (clientId == null || clientSecret == null || apiToken == null || apiProfile == null) {
            throw new IllegalStateException("Firefox Account info not retrieved");
        }
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.apiToken = apiToken;
        this.apiProfile = apiProfile;
    }

    String getClientId() {
        return clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    String getApiToken() {
        return apiToken;
    }

    String getApiProfile() {
        return apiProfile;
    }
}
