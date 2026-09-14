package com.github.daraja.dto.c2b;

import com.fasterxml.jackson.annotation.JsonProperty;

public record C2BCallbackRequest(
        @JsonProperty("TransactionType") String transactionType,
        @JsonProperty("TransID") String transId,
        @JsonProperty("TransTime") String transTime,
        @JsonProperty("TransAmount") String transAmount,
        @JsonProperty("BusinessShortCode") String businessShortCode,
        @JsonProperty("BillRefNumber") String billRefNumber,
        @JsonProperty("InvoiceNumber") String invoiceNumber,
        @JsonProperty("OrgAccountBalance") String orgAccountBalance,
        @JsonProperty("ThirdPartyTransID") String thirdPartyTransId,
        @JsonProperty("MSISDN") String msisdn,
        @JsonProperty("FirstName") String firstName,
        @JsonProperty("MiddleName") String middleName,
        @JsonProperty("LastName") String lastName
) {}
