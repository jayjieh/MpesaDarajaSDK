# B2C (Business to Customer) API

**Status: Not Started**

B2C enables businesses to send money to customers. Common use cases: salary payments, refunds, promotional payouts, supplier payments.

## Endpoint

| Operation | Method | Endpoint |
|-----------|--------|----------|
| B2C Payment | POST | `/mpesa/b2c/v1/paymentrequest` |

## Request Parameters

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| InitiatorName | String | Yes | API operator username |
| SecurityCredential | String | Yes | Encrypted initiator password |
| CommandID | String | Yes | Payment type (see below) |
| Amount | String | Yes | Payment amount |
| PartyA | String | Yes | Organization shortcode |
| PartyB | String | Yes | Recipient phone (254XXXXXXXXX) |
| Remarks | String | Yes | Transaction description |
| QueueTimeOutURL | String | Yes | Timeout callback URL |
| ResultURL | String | Yes | Result callback URL |
| Occasion | String | No | Optional description |

### CommandID Options

| CommandID | Description |
|-----------|-------------|
| BusinessPayment | General payments |
| SalaryPayment | Salary disbursements |
| PromotionPayment | Promotional payouts |

## Request Example

```json
{
  "InitiatorName": "testapi",
  "SecurityCredential": "EncryptedPasswordHere...",
  "CommandID": "BusinessPayment",
  "Amount": "100",
  "PartyA": "600000",
  "PartyB": "254712345678",
  "Remarks": "Refund for order 12345",
  "QueueTimeOutURL": "https://example.com/timeout",
  "ResultURL": "https://example.com/result",
  "Occasion": "Refund"
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
        { "Key": "TransactionAmount", "Value": 100 },
        { "Key": "TransactionReceipt", "Value": "NLJ41HAY6Q" },
        { "Key": "B2CRecipientIsRegisteredCustomer", "Value": "Y" },
        { "Key": "B2CChargesPaidAccountAvailableFunds", "Value": 1000.00 },
        { "Key": "ReceiverPartyPublicName", "Value": "254712345678 - John Doe" },
        { "Key": "TransactionCompletedDateTime", "Value": "19.12.2019 10:45:50" },
        { "Key": "B2CUtilityAccountAvailableFunds", "Value": 5000.00 },
        { "Key": "B2CWorkingAccountAvailableFunds", "Value": 10000.00 }
      ]
    }
  }
}
```

### Failed Payment

```json
{
  "Result": {
    "ResultType": 0,
    "ResultCode": 2001,
    "ResultDesc": "The initiator information is invalid.",
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
| 1 | Insufficient balance |
| 2001 | Invalid initiator credentials |
| 2006 | Service unavailable |

## Security Credential Generation

B2C requires encrypting the initiator password with Safaricom's public certificate.

### Certificate Files

| Environment | Certificate |
|-------------|-------------|
| Sandbox | SandboxCertificate.cer |
| Production | ProductionCertificate.cer |

Download from: [Daraja Portal](https://developer.safaricom.co.ke/APIs/Authorization)

### Encryption Code

```java
// util/DarajaUtils.java - Add this method

public static String encryptSecurityCredential(String password, DarajaProperties.Environment environment) {
    try {
        String certPath = environment == DarajaProperties.Environment.SANDBOX
            ? "certs/SandboxCertificate.cer"
            : "certs/ProductionCertificate.cer";

        InputStream certStream = DarajaUtils.class.getClassLoader().getResourceAsStream(certPath);
        if (certStream == null) {
            throw new DarajaApiException("Certificate not found: " + certPath);
        }

        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        X509Certificate certificate = (X509Certificate) cf.generateCertificate(certStream);
        PublicKey publicKey = certificate.getPublicKey();

        Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        cipher.init(Cipher.ENCRYPT_MODE, publicKey);
        byte[] encrypted = cipher.doFinal(password.getBytes(StandardCharsets.UTF_8));

        return Base64.getEncoder().encodeToString(encrypted);
    } catch (Exception e) {
        throw new DarajaApiException("Failed to encrypt security credential: " + e.getMessage(), e);
    }
}
```

## Implementation Guide

### 1. Create DTOs

```java
// b2c/B2CRequest.java
package com.github.daraja.dto.b2c;

import com.fasterxml.jackson.annotation.JsonProperty;

public record B2CRequest(
    @JsonProperty("InitiatorName") String initiatorName,
    @JsonProperty("SecurityCredential") String securityCredential,
    @JsonProperty("CommandID") String commandId,
    @JsonProperty("Amount") String amount,
    @JsonProperty("PartyA") String partyA,
    @JsonProperty("PartyB") String partyB,
    @JsonProperty("Remarks") String remarks,
    @JsonProperty("QueueTimeOutURL") String queueTimeOutUrl,
    @JsonProperty("ResultURL") String resultUrl,
    @JsonProperty("Occasion") String occasion
) {}

// b2c/B2CResponse.java
public record B2CResponse(
    @JsonProperty("ConversationID") String conversationId,
    @JsonProperty("OriginatorConversationID") String originatorConversationId,
    @JsonProperty("ResponseCode") String responseCode,
    @JsonProperty("ResponseDescription") String responseDescription
) {}

// b2c/B2CResultCallback.java
public record B2CResultCallback(
    @JsonProperty("Result") B2CResult result
) {}

public record B2CResult(
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
// client/DarajaB2CClient.java
package com.github.daraja.client;

import com.github.daraja.autoconfig.DarajaProperties;
import com.github.daraja.dto.b2c.*;
import com.github.daraja.exception.DarajaApiException;
import com.github.daraja.util.DarajaUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

public class DarajaB2CClient {

    private final RestClient restClient;
    private final DarajaAuthClient authClient;
    private final DarajaProperties properties;

    public DarajaB2CClient(RestClient.Builder builder, DarajaAuthClient authClient, DarajaProperties properties) {
        this.authClient = authClient;
        this.properties = properties;
        this.restClient = builder
                .baseUrl(properties.getEnvironment().getBaseUrl())
                .build();
    }

    public B2CResponse sendPayment(String phone, String amount, String commandId,
                                    String remarks, String resultUrl, String timeoutUrl) {
        if (properties.getInitiatorName() == null || properties.getInitiatorPassword() == null) {
            throw new DarajaApiException("B2C requires initiatorName and initiatorPassword configuration");
        }

        String securityCredential = DarajaUtils.encryptSecurityCredential(
            properties.getInitiatorPassword(),
            properties.getEnvironment()
        );

        B2CRequest request = new B2CRequest(
            properties.getInitiatorName(),
            securityCredential,
            commandId,
            amount,
            properties.getBusinessShortCode(),
            DarajaUtils.sanitizePhoneNumber(phone),
            remarks,
            timeoutUrl,
            resultUrl,
            ""
        );

        try {
            return restClient.post()
                .uri("/mpesa/b2c/v1/paymentrequest")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + authClient.getAccessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(B2CResponse.class);
        } catch (Exception e) {
            throw new DarajaApiException("B2C payment failed: " + e.getMessage(), e);
        }
    }

    // Convenience methods
    public B2CResponse sendBusinessPayment(String phone, String amount, String remarks,
                                            String resultUrl, String timeoutUrl) {
        return sendPayment(phone, amount, "BusinessPayment", remarks, resultUrl, timeoutUrl);
    }

    public B2CResponse sendSalaryPayment(String phone, String amount, String remarks,
                                          String resultUrl, String timeoutUrl) {
        return sendPayment(phone, amount, "SalaryPayment", remarks, resultUrl, timeoutUrl);
    }

    public B2CResponse sendPromotionPayment(String phone, String amount, String remarks,
                                             String resultUrl, String timeoutUrl) {
        return sendPayment(phone, amount, "PromotionPayment", remarks, resultUrl, timeoutUrl);
    }
}
```

### 3. Create Callback Controller

```java
@RestController
@RequestMapping("/api/v1/mpesa/b2c")
public class B2CCallbackController {

    @PostMapping("/result")
    public ResponseEntity<Void> handleResult(@RequestBody B2CResultCallback callback) {
        if (callback.result().isSuccessful()) {
            // Process successful payment
            String transactionId = callback.result().transactionId();
            // Update your records
        } else {
            // Handle failed payment
            String error = callback.result().resultDesc();
            // Log or retry
        }
        return ResponseEntity.ok().build();
    }

    @PostMapping("/timeout")
    public ResponseEntity<Void> handleTimeout(@RequestBody B2CResultCallback callback) {
        // Handle timeout - queue for retry or manual review
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

## Resource Setup

Place certificates in:
```
src/main/resources/certs/
├── SandboxCertificate.cer
└── ProductionCertificate.cer
```

## Sandbox Test Credentials

| Field | Value |
|-------|-------|
| ShortCode | 600XXX |
| InitiatorName | testapi |
| Test Phone | 254708374149 |
