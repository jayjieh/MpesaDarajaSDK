package com.github.daraja.dto.c2b;

import com.fasterxml.jackson.annotation.JsonProperty;

public record C2BValidationResponse(
        @JsonProperty("ResultCode") String resultCode,
        @JsonProperty("ResultDesc") String resultDesc
) {
    public static C2BValidationResponse accept() {
        return new C2BValidationResponse("0", "Accepted");
    }

    public static C2BValidationResponse reject(String reason) {
        return new C2BValidationResponse("C2B00011", reason != null ? reason : "Rejected");
    }
}
