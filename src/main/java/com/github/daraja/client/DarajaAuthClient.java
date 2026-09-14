package com.github.daraja.client;

import com.github.daraja.autoconfig.DarajaProperties;
import com.github.daraja.dto.auth.AuthResponse;
import com.github.daraja.exception.DarajaApiException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;

public class DarajaAuthClient {

    private final RestClient restClient;
    private final DarajaProperties properties;
    private final AtomicReference<CachedToken> cachedToken = new AtomicReference<>();

    public DarajaAuthClient(RestClient.Builder restClientBuilder, DarajaProperties properties) {
        this.properties = properties;
        this.restClient = restClientBuilder
                .baseUrl(properties.getEnvironment().getBaseUrl())
                .build();
    }

    public String getAccessToken() {
        CachedToken current = cachedToken.get();
        if (current != null && current.isValid()) {
            return current.token();
        }

        synchronized (this) {
            current = cachedToken.get();
            if (current != null && current.isValid()) {
                return current.token();
            }

            if (properties.getConsumerKey() == null || properties.getConsumerSecret() == null) {
                throw new DarajaApiException("Daraja consumer key and consumer secret must be configured.");
            }

            String credentials = properties.getConsumerKey() + ":" + properties.getConsumerSecret();
            String basicAuth = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));

            AuthResponse response;
            try {
                response = restClient.get()
                        .uri("/oauth/v1/generate?grant_type=client_credentials")
                        .header(HttpHeaders.AUTHORIZATION, "Basic " + basicAuth)
                        .accept(MediaType.APPLICATION_JSON)
                        .retrieve()
                        .body(AuthResponse.class);
            } catch (Exception e) {
                throw new DarajaApiException("Failed to fetch OAuth token from Safaricom Daraja: " + e.getMessage(), e);
            }

            if (response == null || response.accessToken() == null) {
                throw new DarajaApiException("Empty access token received from Safaricom Daraja.");
            }

            long expiresInSeconds = Long.parseLong(response.expiresIn());
            // Expire 60 seconds early to prevent edge timeouts
            Instant expiry = Instant.now().plusSeconds(Math.max(10, expiresInSeconds - 60));
            cachedToken.set(new CachedToken(response.accessToken(), expiry));

            return response.accessToken();
        }
    }

    private record CachedToken(String token, Instant expiresAt) {
        boolean isValid() {
            return Instant.now().isBefore(expiresAt);
        }
    }
}
