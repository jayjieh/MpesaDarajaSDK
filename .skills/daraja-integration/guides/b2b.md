# B2B (Business to Business) API

**Status: Not Started**

B2B enables transfers between businesses. Use cases: paying suppliers, transferring between company accounts, purchasing goods.

## Endpoint

| Operation | Method | Endpoint |
|-----------|--------|----------|
| B2B Payment | POST | `/mpesa/b2b/v1/paymentrequest` |

## Request Parameters

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| Initiator | String | Yes | API operator username |
| SecurityCredential | String | Yes | Encrypted initiator password |
| CommandID | String | Yes | Transaction type (see below) |
| SenderIdentifierType | String | Yes | 4 (Shortcode) |
| RecieverIdentifierType | String | Yes | 4 (Shortcode) |
| Amount | String | Yes | Payment amount |
| PartyA | String | Yes | Sender shortcode |
| PartyB | String | Yes | Receiver shortcode |
| AccountReference | String | Yes | Account reference |
| Remarks | String | Yes | Transaction description |
| QueueTimeOutURL | String | Yes | Timeout callback URL |
| ResultURL | String | Yes | Result callback URL |

### CommandID Options

| CommandID | Description |
|-----------|-------------|
| BusinessPayBill | Pay to a PayBill number |
| BusinessBuyGoods | Pay to a Till number |
| DisburseFundsToBusiness | Transfer between shortcodes |
| BusinessToBusinessTransfer | Direct B2B transfer |
| MerchantToMerchantTransfer | Merchant transfers |

## Request Example

```json
{
  "Initiator": "testapi",
  "SecurityCredential": "EncryptedPasswordHere...",
  "CommandID": "BusinessPayBill",
  "SenderIdentifierType": "4",
  "RecieverIdentifierType": "4",
  "Amount": "1000",
  "PartyA": "600000",
  "PartyB": "600001",
  "AccountReference": "INV12345",
  "Remarks": "Supplier payment",
  "QueueTimeOutURL": "https://example.com/timeout",
  "ResultURL": "https://example.com/result"
}
```

## Response

```json
{
  "ConversationID": "AG_20191219_00004e48cf7e3533",
  "OriginatorConversationID": "29115-34620561-1",
  "ResponseCode": "0",
  "ResponseDescription": "Accept the service request successfully."
}
```

## Result Callback

### Successful Payment

```json
{
  "Result": {
    "ResultType": 0,
    "ResultCode": 0,
    "ResultDesc": "The service request is processed successfully.",
    "OriginatorConversationID": "29115-34620561-1",
    "ConversationID": "AG_20191219_00004e48cf7e3533",
    "TransactionID": "NLJ41HAY6Q",
    "ResultParameters": {
      "ResultParameter": [
        { "Key": "InitiatorAccountCurrentBalance", "Value": "10000.00" },
        { "Key": "DebitAccountCurrentBalance", "Value": "9000.00" },
        { "Key": "Amount", "Value": 1000 },
        { "Key": "DebitPartyAffectedAccountBalance", "Value": "Working Account|KES|9000.00" },
        { "Key": "TransCompletedTime", "Value": "20191219104550" },
        { "Key": "DebitPartyCharges", "Value": "0.00" },
        { "Key": "ReceiverPartyPublicName", "Value": "600001 - Test Business" },
        { "Key": "Currency", "Value": "KES" }
      ]
    }
  }
}
```

### Common Result Codes

| Code | Description |
|------|-------------|
| 0 | Success |
| 1 | Insufficient balance |
| 2001 | Invalid initiator credentials |
| 2026 | Transaction limit exceeded |

## Implementation Guide

### 1. Create DTOs

```java
// b2b/B2BRequest.java
package com.github.daraja.dto.b2b;

import com.fasterxml.jackson.annotation.JsonProperty;

public record B2BRequest(
    @JsonProperty("Initiator") String initiator,
    @JsonProperty("SecurityCredential") String securityCredential,
    @JsonProperty("CommandID") String commandId,
    @JsonProperty("SenderIdentifierType") String senderIdentifierType,
    @JsonProperty("RecieverIdentifierType") String receiverIdentifierType,
    @JsonProperty("Amount") String amount,
    @JsonProperty("PartyA") String partyA,
    @JsonProperty("PartyB") String partyB,
    @JsonProperty("AccountReference") String accountReference,
    @JsonProperty("Remarks") String remarks,
    @JsonProperty("QueueTimeOutURL") String queueTimeOutUrl,
    @JsonProperty("ResultURL") String resultUrl
) {}

// b2b/B2BResponse.java
public record B2BResponse(
    @JsonProperty("ConversationID") String conversationId,
    @JsonProperty("OriginatorConversationID") String originatorConversationId,
    @JsonProperty("ResponseCode") String responseCode,
    @JsonProperty("ResponseDescription") String responseDescription
) {}

// b2b/B2BResultCallback.java
public record B2BResultCallback(
    @JsonProperty("Result") B2BResult result
) {}

public record B2BResult(
    @JsonProperty("ResultType") int resultType,
    @JsonProperty("ResultCode") int resultCode,
    @JsonProperty("ResultDesc") String resultDesc,
    @JsonProperty("OriginatorConversationID") String originatorConversationId,
    @JsonProperty("ConversationID") String conversationId,
    @JsonProperty("TransactionID") String transactionId,
    @JsonProperty("ResultParameters") ResultParameters resultParameters
) {
    public boolean isSuccessful() {
        return resultCode == 0;
    }
}
```

### 2. Create Client

```java
// client/DarajaB2BClient.java
package com.github.daraja.client;

import com.github.daraja.autoconfig.DarajaProperties;
import com.github.daraja.dto.b2b.*;
import com.github.daraja.exception.DarajaApiException;
import com.github.daraja.util.DarajaUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

public class DarajaB2BClient {

    private final RestClient restClient;
    private final DarajaAuthClient authClient;
    private final DarajaProperties properties;

    public DarajaB2BClient(RestClient.Builder builder, DarajaAuthClient authClient, DarajaProperties properties) {
        this.authClient = authClient;
        this.properties = properties;
        this.restClient = builder
                .baseUrl(properties.getEnvironment().getBaseUrl())
                .build();
    }

    public B2BResponse sendPayment(String receiverShortCode, String amount, String commandId,
                                    String accountReference, String remarks,
                                    String resultUrl, String timeoutUrl) {
        if (properties.getInitiatorName() == null || properties.getInitiatorPassword() == null) {
            throw new DarajaApiException("B2B requires initiatorName and initiatorPassword configuration");
        }

        String securityCredential = DarajaUtils.encryptSecurityCredential(
            properties.getInitiatorPassword(),
            properties.getEnvironment()
        );

        B2BRequest request = new B2BRequest(
            properties.getInitiatorName(),
            securityCredential,
            commandId,
            "4",  // Shortcode
            "4",  // Shortcode
            amount,
            properties.getBusinessShortCode(),
            receiverShortCode,
            accountReference,
            remarks,
            timeoutUrl,
            resultUrl
        );

        try {
            return restClient.post()
                .uri("/mpesa/b2b/v1/paymentrequest")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + authClient.getAccessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(B2BResponse.class);
        } catch (Exception e) {
            throw new DarajaApiException("B2B payment failed: " + e.getMessage(), e);
        }
    }

    // Convenience methods
    public B2BResponse payBill(String receiverShortCode, String amount, String accountReference,
                                String remarks, String resultUrl, String timeoutUrl) {
        return sendPayment(receiverShortCode, amount, "BusinessPayBill",
                           accountReference, remarks, resultUrl, timeoutUrl);
    }

    public B2BResponse buyGoods(String receiverShortCode, String amount, String accountReference,
                                 String remarks, String resultUrl, String timeoutUrl) {
        return sendPayment(receiverShortCode, amount, "BusinessBuyGoods",
                           accountReference, remarks, resultUrl, timeoutUrl);
    }
}
```

### 3. Create Callback Controller

```java
@RestController
@RequestMapping("/api/v1/mpesa/b2b")
public class B2BCallbackController {

    @PostMapping("/result")
    public ResponseEntity<Void> handleResult(@RequestBody B2BResultCallback callback) {
        if (callback.result().isSuccessful()) {
            String transactionId = callback.result().transactionId();
            // Update your records
        } else {
            String error = callback.result().resultDesc();
            // Log or handle failure
        }
        return ResponseEntity.ok().build();
    }

    @PostMapping("/timeout")
    public ResponseEntity<Void> handleTimeout(@RequestBody B2BResultCallback callback) {
        // Handle timeout
        return ResponseEntity.ok().build();
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
  initiator-name: "testapi"
  initiator-password: ${DARAJA_INITIATOR_PASSWORD}
```

## Sandbox Test Credentials

| Field | Value |
|-------|-------|
| PartyA ShortCode | 600XXX |
| PartyB ShortCode | 600XXX (any valid shortcode) |
| InitiatorName | testapi |
