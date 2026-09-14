package com.github.daraja.dto.stk;

import com.fasterxml.jackson.annotation.JsonProperty;

public record StkPushResponse(
        @JsonProperty("MerchantRequestID") String merchantRequestId,
        @JsonProperty("CheckoutRequestID") String checkoutRequestId,
        @JsonProperty("ResponseCode") String responseCode,
        @JsonProperty("ResponseDescription") String responseDescription,
        @JsonProperty("CustomerMessage") String customerMessage
) {
    public boolean isAccepted() {
        return "0".equals(responseCode);
    }
}
