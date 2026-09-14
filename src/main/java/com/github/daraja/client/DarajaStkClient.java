package com.github.daraja.client;

import com.github.daraja.autoconfig.DarajaProperties;
import com.github.daraja.dto.stk.*;
import com.github.daraja.exception.DarajaApiException;
import com.github.daraja.util.DarajaUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

public class DarajaStkClient {

    private final RestClient restClient;
    private final DarajaAuthClient authClient;
    private final DarajaProperties properties;

    public DarajaStkClient(RestClient.Builder builder, DarajaAuthClient authClient, DarajaProperties properties) {
        this.authClient = authClient;
        this.properties = properties;
        this.restClient = builder
                .baseUrl(properties.getEnvironment().getBaseUrl())
                .build();
    }

    public StkPushResponse sendStkPush(String phone, String amount, String accountRef, String desc) {
        return sendStkPush(phone, amount, accountRef, desc, properties.getDefaultCallbackUrl());
    }

    public StkPushResponse sendStkPush(String phone, String amount, String accountRef, String desc, String callbackUrl) {
        if (properties.getBusinessShortCode() == null || properties.getPasskey() == null) {
            throw new DarajaApiException("BusinessShortCode and Passkey must be configured in daraja properties.");
        }

        String targetCallback = (callbackUrl != null && !callbackUrl.isBlank()) ? callbackUrl : properties.getDefaultCallbackUrl();
        if (targetCallback == null || targetCallback.isBlank()) {
            throw new DarajaApiException("Callback URL must be provided or configured in defaultCallbackUrl.");
        }

        String timestamp = DarajaUtils.getTimestamp();
        String password = DarajaUtils.generateStkPassword(properties.getBusinessShortCode(), properties.getPasskey(), timestamp);
        String formattedPhone = DarajaUtils.sanitizePhoneNumber(phone);

        StkPushRequest request = new StkPushRequest(
                properties.getBusinessShortCode(),
                password,
                timestamp,
                "CustomerPayBillOnline",
                amount,
                formattedPhone,
                properties.getBusinessShortCode(),
                formattedPhone,
                targetCallback,
                accountRef,
                desc
        );

        try {
            return restClient.post()
                    .uri("/mpesa/stkpush/v1/processrequest")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + authClient.getAccessToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(StkPushResponse.class);
        } catch (Exception e) {
            throw new DarajaApiException("STK Push failed: " + e.getMessage(), e);
        }
    }

    public StkQueryResponse queryStatus(String checkoutRequestId) {
        String timestamp = DarajaUtils.getTimestamp();
        String password = DarajaUtils.generateStkPassword(properties.getBusinessShortCode(), properties.getPasskey(), timestamp);

        StkQueryRequest request = new StkQueryRequest(
                properties.getBusinessShortCode(),
                password,
                timestamp,
                checkoutRequestId
        );

        try {
            return restClient.post()
                    .uri("/mpesa/stkpushquery/v1/query")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + authClient.getAccessToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(StkQueryResponse.class);
        } catch (Exception e) {
            throw new DarajaApiException("STK Query failed: " + e.getMessage(), e);
        }
    }
}
