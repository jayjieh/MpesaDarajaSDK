package com.github.daraja.dto.stk;

import com.fasterxml.jackson.annotation.JsonProperty;

public record StkQueryResponse(
        @JsonProperty("ResponseCode") String responseCode,
        @JsonProperty("ResponseDescription") String responseDescription,
        @JsonProperty("MerchantRequestID") String merchantRequestId,
        @JsonProperty("CheckoutRequestID") String checkoutRequestId,
        @JsonProperty("ResultCode") String resultCode,
        @JsonProperty("ResultDesc") String resultDesc
) {
    public boolean isSuccessful() {
        return "0".equals(resultCode);
    }
}
