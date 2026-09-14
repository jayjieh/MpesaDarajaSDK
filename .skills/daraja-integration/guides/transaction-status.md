# Transaction Status API

**Status: Not Started**

Query the status of any M-Pesa transaction using the transaction ID.

## Endpoint

| Operation | Method | Endpoint |
|-----------|--------|----------|
| Query Status | POST | `/mpesa/transactionstatus/v1/query` |

## Request Parameters

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| Initiator | String | Yes | API operator username |
| SecurityCredential | String | Yes | Encrypted initiator password |
| CommandID | String | Yes | Always `TransactionStatusQuery` |
| TransactionID | String | Yes | M-Pesa transaction ID to query |
| PartyA | String | Yes | Organization shortcode |
| IdentifierType | String | Yes | 4 (Shortcode) |
| ResultURL | String | Yes | Result callback URL |
| QueueTimeOutURL | String | Yes | Timeout callback URL |
| Remarks | String | Yes | Query description |
| Occasion | String | No | Optional description |

## Request Example

```json
{
  "Initiator": "testapi",
  "SecurityCredential": "EncryptedPasswordHere...",
  "CommandID": "TransactionStatusQuery",
  "TransactionID": "NLJ41HAY6Q",
  "PartyA": "600000",
  "IdentifierType": "4",
  "ResultURL": "https://example.com/result",
  "QueueTimeOutURL": "https://example.com/timeout",
  "Remarks": "Status query",
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

### Successful Query

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
        { "Key": "ReceiptNo", "Value": "NLJ41HAY6Q" },
        { "Key": "Conversation ID", "Value": "AG_20191219_00004e48cf7e3533" },
        { "Key": "FinalisedTime", "Value": "20191219104550" },
        { "Key": "Amount", "Value": 100 },
        { "Key": "TransactionStatus", "Value": "Completed" },
        { "Key": "ReasonType", "Value": "Salary Payment" },
        { "Key": "TransactionReason", "Value": "" },
        { "Key": "DebitPartyCharges", "Value": "0.00" },
        { "Key": "DebitAccountType", "Value": "Utility Account" },
        { "Key": "InitiatedTime", "Value": "20191219104530" },
        { "Key": "Originator Conversation ID", "Value": "29115-34620561-1" },
        { "Key": "CreditPartyName", "Value": "254712345678 - John Doe" },
        { "Key": "DebitPartyName", "Value": "600000 - Test Business" }
      ]
    }
  }
}
```

### Transaction Status Values

| Status | Description |
|--------|-------------|
| Completed | Successfully completed |
| Failed | Transaction failed |
| Reversed | Transaction was reversed |
| Pending | Still processing |

### Common Result Codes

| Code | Description |
|------|-------------|
| 0 | Success |
| 2001 | Invalid initiator credentials |
| 1001 | Transaction not found |

## Implementation Guide

### 1. Create DTOs

```java
// query/TransactionStatusRequest.java
package com.github.daraja.dto.query;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TransactionStatusRequest(
    @JsonProperty("Initiator") String initiator,
    @JsonProperty("SecurityCredential") String securityCredential,
    @JsonProperty("CommandID") String commandId,
    @JsonProperty("TransactionID") String transactionId,
    @JsonProperty("PartyA") String partyA,
    @JsonProperty("IdentifierType") String identifierType,
    @JsonProperty("ResultURL") String resultUrl,
    @JsonProperty("QueueTimeOutURL") String queueTimeOutUrl,
    @JsonProperty("Remarks") String remarks,
    @JsonProperty("Occasion") String occasion
) {}

// query/TransactionStatusResponse.java
public record TransactionStatusResponse(
    @JsonProperty("ConversationID") String conversationId,
    @JsonProperty("OriginatorConversationID") String originatorConversationId,
    @JsonProperty("ResponseCode") String responseCode,
    @JsonProperty("ResponseDescription") String responseDescription
) {}

// query/TransactionStatusCallback.java
public record TransactionStatusCallback(
    @JsonProperty("Result") TransactionStatusResult result
) {}

public record TransactionStatusResult(
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

    public String getTransactionStatus() {
        return resultParameters.getParameterValue("TransactionStatus");
    }

    public String getReceiptNo() {
        return resultParameters.getParameterValue("ReceiptNo");
    }

    public String getAmount() {
        return resultParameters.getParameterValue("Amount");
    }
}
```

### 2. Create Client

```java
// client/DarajaQueryClient.java
package com.github.daraja.client;

import com.github.daraja.autoconfig.DarajaProperties;
import com.github.daraja.dto.query.*;
import com.github.daraja.exception.DarajaApiException;
import com.github.daraja.util.DarajaUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

public class DarajaQueryClient {

    private final RestClient restClient;
    private final DarajaAuthClient authClient;
    private final DarajaProperties properties;

    public DarajaQueryClient(RestClient.Builder builder, DarajaAuthClient authClient, DarajaProperties properties) {
        this.authClient = authClient;
        this.properties = properties;
        this.restClient = builder
                .baseUrl(properties.getEnvironment().getBaseUrl())
                .build();
    }

    public TransactionStatusResponse queryTransactionStatus(String transactionId,
                                                             String resultUrl,
                                                             String timeoutUrl) {
        if (properties.getInitiatorName() == null || properties.getInitiatorPassword() == null) {
            throw new DarajaApiException("Transaction status query requires initiator credentials");
        }

        String securityCredential = DarajaUtils.encryptSecurityCredential(
            properties.getInitiatorPassword(),
            properties.getEnvironment()
        );

        TransactionStatusRequest request = new TransactionStatusRequest(
            properties.getInitiatorName(),
            securityCredential,
            "TransactionStatusQuery",
            transactionId,
            properties.getBusinessShortCode(),
            "4",  // Shortcode
            resultUrl,
            timeoutUrl,
            "Transaction status query",
            ""
        );

        try {
            return restClient.post()
                .uri("/mpesa/transactionstatus/v1/query")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + authClient.getAccessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(TransactionStatusResponse.class);
        } catch (Exception e) {
            throw new DarajaApiException("Transaction status query failed: " + e.getMessage(), e);
        }
    }
}
```

### 3. Create Callback Controller

```java
@RestController
@RequestMapping("/api/v1/mpesa/query")
public class TransactionStatusCallbackController {

    @PostMapping("/transaction-status/result")
    public ResponseEntity<Void> handleResult(@RequestBody TransactionStatusCallback callback) {
        if (callback.result().isSuccessful()) {
            String status = callback.result().getTransactionStatus();
            String receiptNo = callback.result().getReceiptNo();
            // Process the transaction status
        }
        return ResponseEntity.ok().build();
    }

    @PostMapping("/transaction-status/timeout")
    public ResponseEntity<Void> handleTimeout(@RequestBody TransactionStatusCallback callback) {
        // Handle timeout - retry or log
        return ResponseEntity.ok().build();
    }
}
```

## Use Cases

1. **Reconciliation**: Build nightly batch jobs to verify transaction states
2. **Callback Failures**: Query status when callbacks fail or are delayed
3. **Dispute Resolution**: Verify transaction details for customer support
4. **Audit Trail**: Confirm transaction completion for record-keeping

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
