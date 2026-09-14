package com.github.daraja.dto.stk;

import com.fasterxml.jackson.annotation.JsonProperty;

public record StkQueryRequest(
        @JsonProperty("BusinessShortCode") String businessShortCode,
        @JsonProperty("Password") String password,
        @JsonProperty("Timestamp") String timestamp,
        @JsonProperty("CheckoutRequestID") String checkoutRequestId
) {}
