package com.github.daraja.dto.callback;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public record StkCallbackWrapper(@JsonProperty("Body") Body body) {

    public record Body(@JsonProperty("stkCallback") StkCallback stkCallback) {}

    public record StkCallback(
            @JsonProperty("MerchantRequestID") String merchantRequestId,
            @JsonProperty("CheckoutRequestID") String checkoutRequestId,
            @JsonProperty("ResultCode") Integer resultCode,
            @JsonProperty("ResultDesc") String resultDesc,
            @JsonProperty("CallbackMetadata") CallbackMetadata callbackMetadata
    ) {
        public boolean isSuccessful() {
            return resultCode != null && resultCode == 0;
        }

        public Map<String, Object> getMetadataAsMap() {
            if (callbackMetadata == null || callbackMetadata.items() == null) {
                return Map.of();
            }
            return callbackMetadata.items().stream()
                    .filter(i -> i.name() != null && i.value() != null)
                    .collect(Collectors.toMap(Item::name, Item::value, (a, b) -> a));
        }

        public String getMpesaReceiptNumber() {
            Object val = getMetadataAsMap().get("MpesaReceiptNumber");
            return val != null ? val.toString() : null;
        }

        public String getAmount() {
            Object val = getMetadataAsMap().get("Amount");
            return val != null ? val.toString() : null;
        }

        public String getPhoneNumber() {
            Object val = getMetadataAsMap().get("PhoneNumber");
            return val != null ? val.toString() : null;
        }
    }

    public record CallbackMetadata(@JsonProperty("Item") List<Item> items) {}
    public record Item(@JsonProperty("Name") String name, @JsonProperty("Value") Object value) {}
}
