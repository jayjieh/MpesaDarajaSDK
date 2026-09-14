# Daraja Spring Boot Starter

A modern, type-safe Spring Boot Starter SDK for integrating with the [Safaricom Daraja Developer Portal](https://developer.safaricom.co.ke/) (M-PESA APIs). Built for Spring Boot 3.x and Java 17+ using `RestClient`.

## Features

- **Automated OAuth 2.0 Management**: Non-blocking in-memory token caching with proactive token renewal before expiration.
- **M-PESA Express (STK Push)**: Direct initiation and query endpoints with automatic timestamp and Base64 password generation.
- **Phone Number Sanitization**: Automatically normalizes phone numbers to standard format (`2547XXXXXXXX` or `2541XXXXXXXX`).
- **Webhook & Callback Ingestion**: Strongly typed Jackson models for STK Push callbacks, metadata extraction, and C2B validation/confirmation responses.
- **Spring Boot 3 Auto-Configuration**: Auto-wires `DarajaAuthClient` and `DarajaStkClient` with configuration properties.

## Installation

Add the dependency to your `pom.xml`:

```xml
<dependency>
    <groupId>com.github.daraja</groupId>
    <artifactId>daraja-spring-boot-starter</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

## Configuration

Configure your Daraja API credentials in `application.yml` or `application.properties`:

```yaml
daraja:
  environment: SANDBOX # Toggle between SANDBOX and PRODUCTION
  consumer-key: ${DARAJA_CONSUMER_KEY}
  consumer-secret: ${DARAJA_CONSUMER_SECRET}
  business-short-code: "174379"
  passkey: "bfb279f9aa9bdbcf158e97dd71a467cd2e0c893059b10f78e6b72ada1ed2c919"
  default-callback-url: "https://yourdomain.com/api/v1/mpesa/stk-callback"
```

## Usage

### 1. Triggering an STK Push (M-PESA Express)

Inject `DarajaStkClient` into your service or controller:

```java
package com.example.payments;

import com.github.daraja.client.DarajaStkClient;
import com.github.daraja.dto.stk.StkPushResponse;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final DarajaStkClient stkClient;

    public PaymentController(DarajaStkClient stkClient) {
        this.stkClient = stkClient;
    }

    @PostMapping("/stk-push")
    public StkPushResponse initiatePayment(
            @RequestParam String phone,
            @RequestParam String amount,
            @RequestParam String accountReference) {

        return stkClient.sendStkPush(
                phone,
                amount,
                accountReference,
                "Payment for order " + accountReference
        );
    }
}
```

### 2. Handling the STK Push Webhook Callback

Receive and process the asynchronous M-PESA payment notification:

```java
package com.example.payments;

import com.github.daraja.dto.callback.StkCallbackWrapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/mpesa")
public class MpesaCallbackController {

    @PostMapping("/stk-callback")
    public ResponseEntity<Void> handleStkCallback(@RequestBody StkCallbackWrapper payload) {
        var callback = payload.body().stkCallback();

        if (callback.isSuccessful()) {
            String mpesaReceipt = callback.getMpesaReceiptNumber();
            String amount = callback.getAmount();
            String phone = callback.getPhoneNumber();
            // Process order fulfillment
        } else {
            // Handle failed/canceled transaction: callback.resultDesc()
        }

        return ResponseEntity.ok().build();
    }
}
```

### 3. Querying STK Push Status

If the callback is delayed or you need to poll for status:

```java
var queryResponse = stkClient.queryStatus("ws_CO_1409202613000012345678");
if (queryResponse.isSuccessful()) {
    // Transaction succeeded
}
```

## Reference Documentation

- [Safaricom Daraja Developer Portal](https://developer.safaricom.co.ke/)
- [M-PESA Getting Started Guide](https://developer.safaricom.co.ke/apis/GettingStarted)

## License

MIT
