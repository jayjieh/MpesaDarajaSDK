# C2B (Customer to Business) API

**Status: Partial (DTOs exist, client not implemented)**

C2B enables customers to pay from M-Pesa menu to PayBill or Till numbers. You register callback URLs to receive payment notifications.

## Endpoints

| Operation | Method | Endpoint |
|-----------|--------|----------|
| Register URLs | POST | `/mpesa/c2b/v1/registerurl` |
| Simulate Payment | POST | `/mpesa/c2b/v1/simulate` |

> **Note:** Simulate endpoint only works in Sandbox environment.

## URL Registration

Register validation and confirmation URLs to receive payment notifications.

### Request Parameters

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| ShortCode | String | Yes | Organization shortcode |
| ResponseType | String | Yes | `Completed` or `Cancelled` |
| ConfirmationURL | String | Yes | HTTPS URL for successful transactions |
| ValidationURL | String | Yes | HTTPS URL for payment validation |

### Request Example

```json
{
  "ShortCode": "600000",
  "ResponseType": "Completed",
  "ConfirmationURL": "https://example.com/confirmation",
  "ValidationURL": "https://example.com/validation"
}
```

### Response

```json
{
  "OriginatorCoversationID": "29115-34620561-1",
  "ResponseCode": "0",
  "ResponseDescription": "Success"
}
```

## Validation URL Callback

Called **before** transaction completes. Return acceptance/rejection.

### Incoming Request

```json
{
  "TransactionType": "Pay Bill",
  "TransID": "RKTQDM7W6S",
  "TransTime": "20191122063845",
  "TransAmount": "10.00",
  "BusinessShortCode": "600000",
  "BillRefNumber": "ACC001",
  "InvoiceNumber": "",
  "OrgAccountBalance": "49197.00",
  "ThirdPartyTransID": "",
  "MSISDN": "254712345678",
  "FirstName": "John",
  "MiddleName": "",
  "LastName": "Doe"
}
```

### Your Response (Accept)

```json
{
  "ResultCode": "0",
  "ResultDesc": "Accepted"
}
```

### Your Response (Reject)

```json
{
  "ResultCode": "C2B00011",
  "ResultDesc": "Invalid account number"
}
```

### Common Rejection Codes

| Code | Description |
|------|-------------|
| C2B00011 | Invalid account number |
| C2B00012 | Invalid amount |
| C2B00013 | Service unavailable |
| C2B00014 | Transaction limit exceeded |

## Confirmation URL Callback

Called **after** transaction completes.

### Incoming Request

```json
{
  "TransactionType": "Pay Bill",
  "TransID": "RKTQDM7W6S",
  "TransTime": "20191122063845",
  "TransAmount": "10.00",
  "BusinessShortCode": "600000",
  "BillRefNumber": "ACC001",
  "InvoiceNumber": "",
  "OrgAccountBalance": "49207.00",
  "ThirdPartyTransID": "",
  "MSISDN": "254712345678",
  "FirstName": "John",
  "MiddleName": "",
  "LastName": "Doe"
}
```

### Your Response

```json
{
  "ResultCode": "0",
  "ResultDesc": "Accepted"
}
```

## Simulate Payment (Sandbox Only)

Test C2B payments in sandbox environment.

### Request Parameters

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| ShortCode | String | Yes | Organization shortcode |
| CommandID | String | Yes | `CustomerPayBillOnline` or `CustomerBuyGoodsOnline` |
| Amount | String | Yes | Transaction amount |
| Msisdn | String | Yes | Customer phone (254XXXXXXXXX) |
| BillRefNumber | String | Yes | Account reference |

### Request Example

```json
{
  "ShortCode": "600000",
  "CommandID": "CustomerPayBillOnline",
  "Amount": "100",
  "Msisdn": "254708374149",
  "BillRefNumber": "ACC001"
}
```

### Response

```json
{
  "OriginatorCoversationID": "29115-34620561-1",
  "ResponseCode": "0",
  "ResponseDescription": "Accept the service request successfully."
}
```

## Implementation Guide

### 1. Create DTOs

```java
// c2b/C2BRegisterUrlRequest.java
package com.github.daraja.dto.c2b;

import com.fasterxml.jackson.annotation.JsonProperty;

public record C2BRegisterUrlRequest(
    @JsonProperty("ShortCode") String shortCode,
    @JsonProperty("ResponseType") String responseType,
    @JsonProperty("ConfirmationURL") String confirmationUrl,
    @JsonProperty("ValidationURL") String validationUrl
) {}

// c2b/C2BRegisterUrlResponse.java
public record C2BRegisterUrlResponse(
    @JsonProperty("OriginatorCoversationID") String originatorConversationId,
    @JsonProperty("ResponseCode") String responseCode,
    @JsonProperty("ResponseDescription") String responseDescription
) {}

// c2b/C2BSimulateRequest.java
public record C2BSimulateRequest(
    @JsonProperty("ShortCode") String shortCode,
    @JsonProperty("CommandID") String commandId,
    @JsonProperty("Amount") String amount,
    @JsonProperty("Msisdn") String msisdn,
    @JsonProperty("BillRefNumber") String billRefNumber
) {}

// c2b/C2BCallbackRequest.java (already exists)
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
```

### 2. Create Client

```java
// client/DarajaC2BClient.java
package com.github.daraja.client;

import com.github.daraja.autoconfig.DarajaProperties;
import com.github.daraja.dto.c2b.*;
import com.github.daraja.exception.DarajaApiException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

public class DarajaC2BClient {

    private final RestClient restClient;
    private final DarajaAuthClient authClient;
    private final DarajaProperties properties;

    public DarajaC2BClient(RestClient.Builder builder, DarajaAuthClient authClient, DarajaProperties properties) {
        this.authClient = authClient;
        this.properties = properties;
        this.restClient = builder
                .baseUrl(properties.getEnvironment().getBaseUrl())
                .build();
    }

    public C2BRegisterUrlResponse registerUrls(String confirmationUrl, String validationUrl) {
        C2BRegisterUrlRequest request = new C2BRegisterUrlRequest(
            properties.getBusinessShortCode(),
            "Completed",
            confirmationUrl,
            validationUrl
        );

        try {
            return restClient.post()
                .uri("/mpesa/c2b/v1/registerurl")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + authClient.getAccessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(C2BRegisterUrlResponse.class);
        } catch (Exception e) {
            throw new DarajaApiException("C2B URL registration failed: " + e.getMessage(), e);
        }
    }

    public C2BRegisterUrlResponse simulate(String amount, String phone, String billRefNumber) {
        if (properties.getEnvironment() != DarajaProperties.Environment.SANDBOX) {
            throw new DarajaApiException("C2B simulation is only available in sandbox environment");
        }

        C2BSimulateRequest request = new C2BSimulateRequest(
            properties.getBusinessShortCode(),
            "CustomerPayBillOnline",
            amount,
            DarajaUtils.sanitizePhoneNumber(phone),
            billRefNumber
        );

        try {
            return restClient.post()
                .uri("/mpesa/c2b/v1/simulate")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + authClient.getAccessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(C2BRegisterUrlResponse.class);
        } catch (Exception e) {
            throw new DarajaApiException("C2B simulation failed: " + e.getMessage(), e);
        }
    }
}
```

### 3. Register in AutoConfiguration

```java
@Bean
@ConditionalOnMissingBean
public DarajaC2BClient darajaC2BClient(RestClient.Builder builder, DarajaAuthClient authClient, DarajaProperties properties) {
    return new DarajaC2BClient(builder, authClient, properties);
}
```

### 4. Create Callback Controller

```java
@RestController
@RequestMapping("/api/v1/mpesa/c2b")
public class C2BCallbackController {

    @PostMapping("/validation")
    public C2BValidationResponse validate(@RequestBody C2BCallbackRequest request) {
        // Validate the payment (check account exists, amount valid, etc.)
        boolean isValid = validatePayment(request);

        if (isValid) {
            return new C2BValidationResponse("0", "Accepted");
        } else {
            return new C2BValidationResponse("C2B00011", "Invalid account");
        }
    }

    @PostMapping("/confirmation")
    public C2BValidationResponse confirm(@RequestBody C2BCallbackRequest request) {
        // Process the confirmed payment
        processPayment(request);
        return new C2BValidationResponse("0", "Accepted");
    }
}
```

## Configuration

```yaml
daraja:
  environment: SANDBOX
  consumer-key: ${DARAJA_CONSUMER_KEY}
  consumer-secret: ${DARAJA_CONSUMER_SECRET}
  business-short-code: "600000"
```

## Sandbox Test Credentials

| Field | Value |
|-------|-------|
| ShortCode | 600XXX (your test shortcode) |
| Test Phone | 254708374149 |
