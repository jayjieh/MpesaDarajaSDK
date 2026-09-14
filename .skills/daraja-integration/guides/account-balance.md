# Account Balance API

**Status: Not Started**

Query the balance of an M-Pesa account (PayBill or Till).

## Endpoint

| Operation | Method | Endpoint |
|-----------|--------|----------|
| Query Balance | POST | `/mpesa/accountbalance/v1/query` |

## Request Parameters

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| Initiator | String | Yes | API operator username |
| SecurityCredential | String | Yes | Encrypted initiator password |
| CommandID | String | Yes | Always `AccountBalance` |
| PartyA | String | Yes | Organization shortcode |
| IdentifierType | String | Yes | 4 (Shortcode) |
| Remarks | String | Yes | Query description |
| QueueTimeOutURL | String | Yes | Timeout callback URL |
| ResultURL | String | Yes | Result callback URL |

## Request Example

```json
{
  "Initiator": "testapi",
  "SecurityCredential": "EncryptedPasswordHere...",
  "CommandID": "AccountBalance",
  "PartyA": "600000",
  "IdentifierType": "4",
  "Remarks": "Balance query",
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
        {
          "Key": "AccountBalance",
          "Value": "Working Account|KES|50000.00|50000.00|0.00|0.00&Utility Account|KES|10000.00|10000.00|0.00|0.00&Charges Paid Account|KES|0.00|0.00|0.00|0.00"
        },
        { "Key": "BOCompletedTime", "Value": "20191219104550" }
      ]
    }
  }
}
```

### Account Balance Format

The `AccountBalance` value contains multiple accounts separated by `&`:
```
AccountName|Currency|AvailableBalance|ActualBalance|ReservedBalance|UnClearedBalance
```

Example breakdown:
- Working Account: KES 50,000.00 available
- Utility Account: KES 10,000.00 available
- Charges Paid Account: KES 0.00

### Common Result Codes

| Code | Description |
|------|-------------|
| 0 | Success |
| 2001 | Invalid initiator credentials |
| 2006 | Service unavailable |

## Implementation Guide

### 1. Create DTOs

```java
// query/AccountBalanceRequest.java
package com.github.daraja.dto.query;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AccountBalanceRequest(
    @JsonProperty("Initiator") String initiator,
    @JsonProperty("SecurityCredential") String securityCredential,
    @JsonProperty("CommandID") String commandId,
    @JsonProperty("PartyA") String partyA,
    @JsonProperty("IdentifierType") String identifierType,
    @JsonProperty("Remarks") String remarks,
    @JsonProperty("QueueTimeOutURL") String queueTimeOutUrl,
    @JsonProperty("ResultURL") String resultUrl
) {}

// query/AccountBalanceResponse.java
public record AccountBalanceResponse(
    @JsonProperty("ConversationID") String conversationId,
    @JsonProperty("OriginatorConversationID") String originatorConversationId,
    @JsonProperty("ResponseCode") String responseCode,
    @JsonProperty("ResponseDescription") String responseDescription
) {}

// query/AccountBalanceCallback.java
public record AccountBalanceCallback(
    @JsonProperty("Result") AccountBalanceResult result
) {}

public record AccountBalanceResult(
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

    public List<AccountInfo> getAccountBalances() {
        String balanceString = resultParameters.getParameterValue("AccountBalance");
        if (balanceString == null) return List.of();

        return Arrays.stream(balanceString.split("&"))
            .map(AccountInfo::parse)
            .filter(Objects::nonNull)
            .toList();
    }
}

// query/AccountInfo.java
public record AccountInfo(
    String accountName,
    String currency,
    BigDecimal availableBalance,
    BigDecimal actualBalance,
    BigDecimal reservedBalance,
    BigDecimal unclearedBalance
) {
    public static AccountInfo parse(String accountString) {
        String[] parts = accountString.split("\\|");
        if (parts.length < 6) return null;

        return new AccountInfo(
            parts[0],
            parts[1],
            new BigDecimal(parts[2]),
            new BigDecimal(parts[3]),
            new BigDecimal(parts[4]),
            new BigDecimal(parts[5])
        );
    }
}
```

### 2. Add to Client

```java
// Add to DarajaQueryClient.java

public AccountBalanceResponse queryAccountBalance(String resultUrl, String timeoutUrl) {
    if (properties.getInitiatorName() == null || properties.getInitiatorPassword() == null) {
        throw new DarajaApiException("Account balance query requires initiator credentials");
    }

    String securityCredential = DarajaUtils.encryptSecurityCredential(
        properties.getInitiatorPassword(),
        properties.getEnvironment()
    );

    AccountBalanceRequest request = new AccountBalanceRequest(
        properties.getInitiatorName(),
        securityCredential,
        "AccountBalance",
        properties.getBusinessShortCode(),
        "4",  // Shortcode
        "Balance query",
        timeoutUrl,
        resultUrl
    );

    try {
        return restClient.post()
            .uri("/mpesa/accountbalance/v1/query")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + authClient.getAccessToken())
            .contentType(MediaType.APPLICATION_JSON)
            .body(request)
            .retrieve()
            .body(AccountBalanceResponse.class);
    } catch (Exception e) {
        throw new DarajaApiException("Account balance query failed: " + e.getMessage(), e);
    }
}
```

### 3. Create Callback Controller

```java
@RestController
@RequestMapping("/api/v1/mpesa/query")
public class AccountBalanceCallbackController {

    @PostMapping("/account-balance/result")
    public ResponseEntity<Void> handleResult(@RequestBody AccountBalanceCallback callback) {
        if (callback.result().isSuccessful()) {
            List<AccountInfo> balances = callback.result().getAccountBalances();
            for (AccountInfo account : balances) {
                log.info("{}: {} {} available",
                    account.accountName(),
                    account.currency(),
                    account.availableBalance());
            }
        }
        return ResponseEntity.ok().build();
    }

    @PostMapping("/account-balance/timeout")
    public ResponseEntity<Void> handleTimeout(@RequestBody AccountBalanceCallback callback) {
        return ResponseEntity.ok().build();
    }
}
```

## Use Cases

1. **Float Management**: Monitor available balance before B2C payouts
2. **Reporting**: Generate daily/weekly balance reports
3. **Alerts**: Trigger notifications when balance drops below threshold
4. **Reconciliation**: Verify expected vs actual balances

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
