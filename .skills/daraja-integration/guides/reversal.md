# Reversal API

**Status: Not Started**

Reverse a completed M-Pesa transaction within the allowed reversal window.

## Endpoint

| Operation | Method | Endpoint |
|-----------|--------|----------|
| Reversal | POST | `/mpesa/reversal/v1/request` |

## Request Parameters

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| Initiator | String | Yes | API operator username |
| SecurityCredential | String | Yes | Encrypted initiator password |
| CommandID | String | Yes | Always `TransactionReversal` |
| TransactionID | String | Yes | M-Pesa transaction ID to reverse |
| Amount | String | Yes | Amount to reverse |
| ReceiverParty | String | Yes | Organization receiving the reversal |
| RecieverIdentifierType | String | Yes | 11 (Organization shortcode) |
| ResultURL | String | Yes | Result callback URL |
| QueueTimeOutURL | String | Yes | Timeout callback URL |
| Remarks | String | Yes | Reversal reason |
| Occasion | String | No | Optional description |

## Request Example

```json
{
  "Initiator": "testapi",
  "SecurityCredential": "EncryptedPasswordHere...",
  "CommandID": "TransactionReversal",
  "TransactionID": "NLJ41HAY6Q",
  "Amount": "100",
  "ReceiverParty": "600000",
  "RecieverIdentifierType": "11",
  "ResultURL": "https://example.com/result",
  "QueueTimeOutURL": "https://example.com/timeout",
  "Remarks": "Customer refund",
  "Occasion": ""
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

### Successful Reversal

```json
{
  "Result": {
    "ResultType": 0,
    "ResultCode": 0,
    "ResultDesc": "The service request is processed successfully.",
    "OriginatorConversationID": "29115-34620561-1",
    "ConversationID": "AG_20191219_00004e48cf7e3533",
    "TransactionID": "NLJ7RT61SV",
    "ResultParameters": {
      "ResultParameter": [
        { "Key": "DebitAccountBalance", "Value": "Utility Account|KES|50000.00" },
        { "Key": "Amount", "Value": 100 },
        { "Key": "TransCompletedTime", "Value": "20191219104550" },
        { "Key": "OriginalTransactionID", "Value": "NLJ41HAY6Q" },
        { "Key": "Charge", "Value": 0 },
        { "Key": "CreditPartyPublicName", "Value": "254712345678 - John Doe" },
        { "Key": "DebitPartyPublicName", "Value": "600000 - Test Business" }
      ]
    }
  }
}
```

### Failed Reversal

```json
{
  "Result": {
    "ResultType": 0,
    "ResultCode": 1,
    "ResultDesc": "The transaction cannot be reversed.",
    "OriginatorConversationID": "29115-34620561-1",
    "ConversationID": "AG_20191219_00004e48cf7e3533",
    "TransactionID": ""
  }
}
```

### Common Result Codes

| Code | Description |
|------|-------------|
| 0 | Success |
| 1 | Transaction cannot be reversed |
| 2001 | Invalid initiator credentials |
| 2015 | Reversal already processed |
| 2016 | Reversal window expired |

## Reversal Constraints

- **Time Limit**: Reversals must be within 24 hours (may vary by account settings)
- **Full Amount**: Must reverse the full transaction amount
- **Single Reversal**: Each transaction can only be reversed once
- **Organization Approval**: Requires proper initiator credentials

## Implementation Guide

### 1. Create DTOs

```java
// reversal/ReversalRequest.java
package com.github.daraja.dto.reversal;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ReversalRequest(
    @JsonProperty("Initiator") String initiator,
    @JsonProperty("SecurityCredential") String securityCredential,
    @JsonProperty("CommandID") String commandId,
    @JsonProperty("TransactionID") String transactionId,
    @JsonProperty("Amount") String amount,
    @JsonProperty("ReceiverParty") String receiverParty,
    @JsonProperty("RecieverIdentifierType") String receiverIdentifierType,
    @JsonProperty("ResultURL") String resultUrl,
    @JsonProperty("QueueTimeOutURL") String queueTimeOutUrl,
    @JsonProperty("Remarks") String remarks,
    @JsonProperty("Occasion") String occasion
) {}

// reversal/ReversalResponse.java
public record ReversalResponse(
    @JsonProperty("ConversationID") String conversationId,
    @JsonProperty("OriginatorConversationID") String originatorConversationId,
    @JsonProperty("ResponseCode") String responseCode,
    @JsonProperty("ResponseDescription") String responseDescription
) {}

// reversal/ReversalCallback.java
public record ReversalCallback(
    @JsonProperty("Result") ReversalResult result
) {}

public record ReversalResult(
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

    public String getOriginalTransactionId() {
        return resultParameters.getParameterValue("OriginalTransactionID");
    }

    public String getAmount() {
        return resultParameters.getParameterValue("Amount");
    }
}
```

### 2. Create Client

```java
// client/DarajaReversalClient.java
package com.github.daraja.client;

import com.github.daraja.autoconfig.DarajaProperties;
import com.github.daraja.dto.reversal.*;
import com.github.daraja.exception.DarajaApiException;
import com.github.daraja.util.DarajaUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

public class DarajaReversalClient {

    private final RestClient restClient;
    private final DarajaAuthClient authClient;
    private final DarajaProperties properties;

    public DarajaReversalClient(RestClient.Builder builder, DarajaAuthClient authClient, DarajaProperties properties) {
        this.authClient = authClient;
        this.properties = properties;
        this.restClient = builder
                .baseUrl(properties.getEnvironment().getBaseUrl())
                .build();
    }

    public ReversalResponse reverseTransaction(String transactionId, String amount,
                                                 String remarks, String resultUrl,
                                                 String timeoutUrl) {
        if (properties.getInitiatorName() == null || properties.getInitiatorPassword() == null) {
            throw new DarajaApiException("Reversal requires initiator credentials");
        }

        String securityCredential = DarajaUtils.encryptSecurityCredential(
            properties.getInitiatorPassword(),
            properties.getEnvironment()
        );

        ReversalRequest request = new ReversalRequest(
            properties.getInitiatorName(),
            securityCredential,
            "TransactionReversal",
            transactionId,
            amount,
            properties.getBusinessShortCode(),
            "11",  // Organization shortcode
            resultUrl,
            timeoutUrl,
            remarks,
            ""
        );

        try {
            return restClient.post()
                .uri("/mpesa/reversal/v1/request")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + authClient.getAccessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(ReversalResponse.class);
        } catch (Exception e) {
            throw new DarajaApiException("Reversal failed: " + e.getMessage(), e);
        }
    }
}
```

### 3. Create Callback Controller

```java
@RestController
@RequestMapping("/api/v1/mpesa/reversal")
public class ReversalCallbackController {

    @PostMapping("/result")
    public ResponseEntity<Void> handleResult(@RequestBody ReversalCallback callback) {
        if (callback.result().isSuccessful()) {
            String originalTxId = callback.result().getOriginalTransactionId();
            String newTxId = callback.result().transactionId();
            // Update your records - mark original as reversed
        } else {
            String error = callback.result().resultDesc();
            // Log reversal failure
        }
        return ResponseEntity.ok().build();
    }

    @PostMapping("/timeout")
    public ResponseEntity<Void> handleTimeout(@RequestBody ReversalCallback callback) {
        // Handle timeout - may need manual review
        return ResponseEntity.ok().build();
    }
}
```

## Use Cases

1. **Customer Refunds**: Reverse accidental or duplicate payments
2. **Failed Deliveries**: Refund when service cannot be provided
3. **Error Correction**: Reverse incorrect amounts or recipients
4. **Dispute Resolution**: Process customer complaints

## Best Practices

1. **Verify First**: Use Transaction Status API before reversing
2. **Track Reversals**: Maintain audit log of all reversals
3. **Idempotency**: Check if reversal already processed before requesting
4. **Timeout Handling**: Implement retry logic for timeouts

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
