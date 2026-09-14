# Dynamic QR Code API

**Status: Not Started**

Generate dynamic QR codes for M-Pesa payments. Customers scan the QR code to pay.

## Endpoint

| Operation | Method | Endpoint |
|-----------|--------|----------|
| Generate QR | POST | `/mpesa/qrcode/v1/generate` |

## Request Parameters

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| MerchantName | String | Yes | Business name (max 25 chars) |
| RefNo | String | Yes | Reference/Order number (max 16 chars) |
| Amount | String | Yes | Payment amount |
| TrxCode | String | Yes | Transaction type (see below) |
| CPI | String | Yes | Credit Party Identifier (Paybill/Till) |
| Size | String | Yes | QR code size (50-300 pixels) |

### Transaction Codes (TrxCode)

| Code | Description |
|------|-------------|
| BG | Buy Goods (Till Number) |
| WA | Withdraw Cash |
| PB | Pay Bill |
| SM | Send Money (mobile) |
| SB | Send to Business |

## Request Example

```json
{
  "MerchantName": "ACME Store",
  "RefNo": "ORDER12345",
  "Amount": "1000",
  "TrxCode": "BG",
  "CPI": "174379",
  "Size": "300"
}
```

## Response

```json
{
  "ResponseCode": "00",
  "RequestID": "29115-34620561-1",
  "ResponseDescription": "The service request is processed successfully.",
  "QRCode": "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAA..."
}
```

The `QRCode` field contains a Base64-encoded PNG image.

### Response Codes

| Code | Description |
|------|-------------|
| 00 | Success |
| 01 | Invalid merchant name |
| 02 | Invalid reference |
| 03 | Invalid amount |
| 04 | Invalid transaction code |
| 05 | Invalid CPI |

## Implementation Guide

### 1. Create DTOs

```java
// qr/QRCodeRequest.java
package com.github.daraja.dto.qr;

import com.fasterxml.jackson.annotation.JsonProperty;

public record QRCodeRequest(
    @JsonProperty("MerchantName") String merchantName,
    @JsonProperty("RefNo") String refNo,
    @JsonProperty("Amount") String amount,
    @JsonProperty("TrxCode") String trxCode,
    @JsonProperty("CPI") String cpi,
    @JsonProperty("Size") String size
) {}

// qr/QRCodeResponse.java
public record QRCodeResponse(
    @JsonProperty("ResponseCode") String responseCode,
    @JsonProperty("RequestID") String requestId,
    @JsonProperty("ResponseDescription") String responseDescription,
    @JsonProperty("QRCode") String qrCode
) {
    public boolean isSuccessful() {
        return "00".equals(responseCode);
    }

    /**
     * Get QR code as byte array for serving as image
     */
    public byte[] getQRCodeBytes() {
        if (qrCode == null) return null;
        String base64Data = qrCode.replace("data:image/png;base64,", "");
        return Base64.getDecoder().decode(base64Data);
    }
}

// qr/TransactionCode.java
public enum TransactionCode {
    BUY_GOODS("BG"),
    WITHDRAW_CASH("WA"),
    PAY_BILL("PB"),
    SEND_MONEY("SM"),
    SEND_TO_BUSINESS("SB");

    private final String code;

    TransactionCode(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
```

### 2. Create Client

```java
// client/DarajaQRClient.java
package com.github.daraja.client;

import com.github.daraja.autoconfig.DarajaProperties;
import com.github.daraja.dto.qr.*;
import com.github.daraja.exception.DarajaApiException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

public class DarajaQRClient {

    private final RestClient restClient;
    private final DarajaAuthClient authClient;
    private final DarajaProperties properties;

    public DarajaQRClient(RestClient.Builder builder, DarajaAuthClient authClient, DarajaProperties properties) {
        this.authClient = authClient;
        this.properties = properties;
        this.restClient = builder
                .baseUrl(properties.getEnvironment().getBaseUrl())
                .build();
    }

    public QRCodeResponse generateQRCode(String merchantName, String refNo, String amount,
                                          TransactionCode trxCode, int size) {
        // Validate inputs
        if (merchantName.length() > 25) {
            throw new DarajaApiException("Merchant name must be max 25 characters");
        }
        if (refNo.length() > 16) {
            throw new DarajaApiException("Reference number must be max 16 characters");
        }
        if (size < 50 || size > 300) {
            throw new DarajaApiException("QR size must be between 50 and 300 pixels");
        }

        QRCodeRequest request = new QRCodeRequest(
            merchantName,
            refNo,
            amount,
            trxCode.getCode(),
            properties.getBusinessShortCode(),
            String.valueOf(size)
        );

        try {
            return restClient.post()
                .uri("/mpesa/qrcode/v1/generate")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + authClient.getAccessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(QRCodeResponse.class);
        } catch (Exception e) {
            throw new DarajaApiException("QR code generation failed: " + e.getMessage(), e);
        }
    }

    // Convenience methods
    public QRCodeResponse generateBuyGoodsQR(String merchantName, String refNo, String amount, int size) {
        return generateQRCode(merchantName, refNo, amount, TransactionCode.BUY_GOODS, size);
    }

    public QRCodeResponse generatePayBillQR(String merchantName, String refNo, String amount, int size) {
        return generateQRCode(merchantName, refNo, amount, TransactionCode.PAY_BILL, size);
    }
}
```

### 3. Create Controller

```java
@RestController
@RequestMapping("/api/payments/qr")
public class QRCodeController {

    private final DarajaQRClient qrClient;

    public QRCodeController(DarajaQRClient qrClient) {
        this.qrClient = qrClient;
    }

    @GetMapping("/generate")
    public ResponseEntity<byte[]> generateQR(
            @RequestParam String orderRef,
            @RequestParam String amount) {

        QRCodeResponse response = qrClient.generateBuyGoodsQR(
            "ACME Store",
            orderRef,
            amount,
            300
        );

        if (!response.isSuccessful()) {
            return ResponseEntity.badRequest().build();
        }

        byte[] qrImage = response.getQRCodeBytes();
        return ResponseEntity.ok()
            .contentType(MediaType.IMAGE_PNG)
            .body(qrImage);
    }

    @GetMapping("/generate/base64")
    public QRCodeResponse generateQRBase64(
            @RequestParam String orderRef,
            @RequestParam String amount) {

        return qrClient.generateBuyGoodsQR(
            "ACME Store",
            orderRef,
            amount,
            300
        );
    }
}
```

### 4. Frontend Integration

```html
<!-- Display QR code from base64 -->
<img id="qrCode" src="" alt="Payment QR Code" />

<script>
async function generateQR(orderRef, amount) {
    const response = await fetch(
        `/api/payments/qr/generate/base64?orderRef=${orderRef}&amount=${amount}`
    );
    const data = await response.json();
    document.getElementById('qrCode').src = data.QRCode;
}
</script>
```

## Use Cases

1. **Point of Sale**: Display QR at checkout for quick payments
2. **E-commerce**: Show QR on order confirmation page
3. **Invoices**: Include QR code on PDF invoices
4. **Events**: Generate QR codes for ticket payments
5. **Donations**: Display QR for donation collection

## Best Practices

1. **Size Selection**: Use 200-300 pixels for print, 150 for mobile display
2. **Timeout**: QR codes don't expire, but set business logic expiry on orders
3. **Verification**: Use C2B callback to confirm payment received
4. **Caching**: Cache generated QR codes for repeated display
5. **Error Handling**: Have fallback payment method if QR fails

## Configuration

```yaml
daraja:
  environment: SANDBOX
  consumer-key: ${DARAJA_CONSUMER_KEY}
  consumer-secret: ${DARAJA_CONSUMER_SECRET}
  business-short-code: "174379"
```

## Important Notes

- QR codes are static once generated
- Payment confirmation comes via C2B callback
- Customer must have M-Pesa app with QR scanning capability
- Works with both Till numbers (Buy Goods) and PayBill
