# Tax Remittance API

**Status: Not Started**

Remit tax directly to Kenya Revenue Authority (KRA) M-Pesa account.

## Endpoint

| Operation | Method | Endpoint |
|-----------|--------|----------|
| Tax Remittance | POST | `/mpesa/b2b/v1/remittax` |

## Request Parameters

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| Initiator | String | Yes | API operator username |
| SecurityCredential | String | Yes | Encrypted initiator password |
| CommandID | String | Yes | Always `PayTaxToKRA` |
| SenderIdentifierType | String | Yes | 4 (Shortcode) |
| RecieverIdentifierType | String | Yes | 4 (Shortcode) |
| Amount | String | Yes | Tax amount |
| PartyA | String | Yes | Your organization shortcode |
| PartyB | String | Yes | KRA PayBill number (572572) |
| AccountReference | String | Yes | KRA PIN (e.g., A123456789B) |
| Remarks | String | Yes | Tax payment description |
| QueueTimeOutURL | String | Yes | Timeout callback URL |
| ResultURL | String | Yes | Result callback URL |

## KRA PayBill Details

| Field | Value |
|-------|-------|
| PayBill Number | 572572 |
| Account Reference | Your KRA PIN |

## Request Example

```json
{
  "Initiator": "testapi",
  "SecurityCredential": "EncryptedPasswordHere...",
  "CommandID": "PayTaxToKRA",
  "SenderIdentifierType": "4",
  "RecieverIdentifierType": "4",
  "Amount": "10000",
  "PartyA": "600000",
  "PartyB": "572572",
  "AccountReference": "A123456789B",
  "Remarks": "VAT Payment Q4 2023",
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
        { "Key": "DebitAccountBalance", "Value": "Working Account|KES|40000.00" },
        { "Key": "Amount", "Value": 10000 },
        { "Key": "TransCompletedTime", "Value": "20191219104550" },
        { "Key": "ReceiverPartyPublicName", "Value": "572572 - Kenya Revenue Authority" },
        { "Key": "DebitPartyPublicName", "Value": "600000 - Test Business" },
        { "Key": "DebitPartyCharges", "Value": "0.00" }
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
| 2006 | Service unavailable |

## Implementation Guide

### 1. Create DTOs

```java
// tax/TaxRemittanceRequest.java
package com.github.daraja.dto.tax;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TaxRemittanceRequest(
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

// tax/TaxRemittanceResponse.java
public record TaxRemittanceResponse(
    @JsonProperty("ConversationID") String conversationId,
    @JsonProperty("OriginatorConversationID") String originatorConversationId,
    @JsonProperty("ResponseCode") String responseCode,
    @JsonProperty("ResponseDescription") String responseDescription
) {}

// tax/TaxRemittanceCallback.java
public record TaxRemittanceCallback(
    @JsonProperty("Result") TaxRemittanceResult result
) {}

public record TaxRemittanceResult(
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
// client/DarajaTaxClient.java
package com.github.daraja.client;

import com.github.daraja.autoconfig.DarajaProperties;
import com.github.daraja.dto.tax.*;
import com.github.daraja.exception.DarajaApiException;
import com.github.daraja.util.DarajaUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

public class DarajaTaxClient {

    private static final String KRA_PAYBILL = "572572";

    private final RestClient restClient;
    private final DarajaAuthClient authClient;
    private final DarajaProperties properties;

    public DarajaTaxClient(RestClient.Builder builder, DarajaAuthClient authClient, DarajaProperties properties) {
        this.authClient = authClient;
        this.properties = properties;
        this.restClient = builder
                .baseUrl(properties.getEnvironment().getBaseUrl())
                .build();
    }

    public TaxRemittanceResponse remitTax(String kraPin, String amount, String remarks,
                                           String resultUrl, String timeoutUrl) {
        if (properties.getInitiatorName() == null || properties.getInitiatorPassword() == null) {
            throw new DarajaApiException("Tax remittance requires initiator credentials");
        }

        // Validate KRA PIN format (e.g., A123456789B)
        if (!kraPin.matches("^[A-Z]\\d{9}[A-Z]$")) {
            throw new DarajaApiException("Invalid KRA PIN format. Expected format: A123456789B");
        }

        String securityCredential = DarajaUtils.encryptSecurityCredential(
            properties.getInitiatorPassword(),
            properties.getEnvironment()
        );

        TaxRemittanceRequest request = new TaxRemittanceRequest(
            properties.getInitiatorName(),
            securityCredential,
            "PayTaxToKRA",
            "4",  // Shortcode
            "4",  // Shortcode
            amount,
            properties.getBusinessShortCode(),
            KRA_PAYBILL,
            kraPin,
            remarks,
            timeoutUrl,
            resultUrl
        );

        try {
            return restClient.post()
                .uri("/mpesa/b2b/v1/remittax")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + authClient.getAccessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(TaxRemittanceResponse.class);
        } catch (Exception e) {
            throw new DarajaApiException("Tax remittance failed: " + e.getMessage(), e);
        }
    }
}
```

### 3. Create Callback Controller

```java
@RestController
@RequestMapping("/api/v1/mpesa/tax")
public class TaxRemittanceCallbackController {

    @PostMapping("/result")
    public ResponseEntity<Void> handleResult(@RequestBody TaxRemittanceCallback callback) {
        if (callback.result().isSuccessful()) {
            String transactionId = callback.result().transactionId();
            // Store for tax records and KRA reconciliation
        } else {
            String error = callback.result().resultDesc();
            // Alert finance team
        }
        return ResponseEntity.ok().build();
    }

    @PostMapping("/timeout")
    public ResponseEntity<Void> handleTimeout(@RequestBody TaxRemittanceCallback callback) {
        // Critical - queue for retry, tax payments must succeed
        return ResponseEntity.ok().build();
    }
}
```

## Tax Types

Common tax payments via M-Pesa to KRA:

| Tax Type | Description |
|----------|-------------|
| VAT | Value Added Tax |
| PAYE | Pay As You Earn |
| WHT | Withholding Tax |
| Excise | Excise Duty |
| Advance Tax | Advance tax payments |

## Best Practices

1. **Verify Amount**: Double-check tax calculations before remitting
2. **Keep Records**: Store transaction IDs for KRA reconciliation
3. **Retry Logic**: Implement robust retry for failed payments
4. **Notifications**: Alert finance team on both success and failure
5. **Audit Trail**: Maintain comprehensive logs for tax audits

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

## Important Notes

- Tax remittance is a B2B variant specifically for KRA payments
- Always verify the KRA PIN before initiating payment
- Keep transaction receipts for tax filing and audits
- Contact KRA for any discrepancies in received amounts
