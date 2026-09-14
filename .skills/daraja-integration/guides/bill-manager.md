# Bill Manager API

**Status: Not Started**

Bill Manager enables businesses to send invoices and receive payments via M-Pesa. Customers receive invoice notifications and can pay directly.

## Endpoints

| Operation | Method | Endpoint |
|-----------|--------|----------|
| Onboard | POST | `/v1/billmanager-invoice/optin` |
| Send Invoice | POST | `/v1/billmanager-invoice/single-invoicing` |
| Bulk Invoices | POST | `/v1/billmanager-invoice/bulk-invoicing` |
| Reconciliation | POST | `/v1/billmanager-invoice/reconciliation` |
| Cancel Invoice | POST | `/v1/billmanager-invoice/cancel-single-invoice` |

## Step 1: Onboard (One-time Setup)

Register your business for Bill Manager.

### Request Parameters

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| shortcode | String | Yes | Your PayBill shortcode |
| email | String | Yes | Business email |
| officialContact | String | Yes | Business phone |
| sendReminders | String | Yes | "0" or "1" (enable reminders) |
| logo | String | No | Base64 encoded logo |
| callbackUrl | String | Yes | Payment notification URL |

### Request Example

```json
{
  "shortcode": "600000",
  "email": "billing@example.com",
  "officialContact": "254712345678",
  "sendReminders": "1",
  "logo": "data:image/png;base64,...",
  "callbackUrl": "https://example.com/bill-callback"
}
```

### Response

```json
{
  "rescode": "200",
  "resmsg": "success"
}
```

## Step 2: Send Invoice

### Request Parameters

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| externalReference | String | Yes | Your invoice ID |
| billedFullName | String | Yes | Customer name |
| billedPhoneNumber | String | Yes | Customer phone (254XXXXXXXXX) |
| billedPeriod | String | Yes | Billing period (e.g., "January 2024") |
| invoiceName | String | Yes | Invoice description |
| dueDate | String | Yes | Due date (YYYY-MM-DD) |
| accountReference | String | Yes | Account/Customer ID |
| amount | String | Yes | Invoice amount |
| invoiceItems | Array | No | Line items |

### Invoice Item Structure

```json
{
  "itemName": "Service Fee",
  "amount": "500"
}
```

### Request Example

```json
{
  "externalReference": "INV-2024-001",
  "billedFullName": "John Doe",
  "billedPhoneNumber": "254712345678",
  "billedPeriod": "January 2024",
  "invoiceName": "Monthly Subscription",
  "dueDate": "2024-01-31",
  "accountReference": "CUST001",
  "amount": "1000",
  "invoiceItems": [
    { "itemName": "Base Subscription", "amount": "800" },
    { "itemName": "Add-on Service", "amount": "200" }
  ]
}
```

### Response

```json
{
  "rescode": "200",
  "resmsg": "success",
  "Status_Message": "Invoice sent successfully"
}
```

## Step 3: Payment Callback

When customer pays the invoice:

```json
{
  "TransactionType": "Pay Bill",
  "TransID": "NLJ41HAY6Q",
  "TransTime": "20240115120000",
  "TransAmount": "1000.00",
  "BusinessShortCode": "600000",
  "BillRefNumber": "INV-2024-001",
  "InvoiceNumber": "INV-2024-001",
  "ThirdPartyTransID": "",
  "MSISDN": "254712345678",
  "FirstName": "John",
  "MiddleName": "",
  "LastName": "Doe",
  "paidInFullFlag": "1",
  "paymentMode": "0"
}
```

### Payment Mode Values

| Value | Description |
|-------|-------------|
| 0 | Full payment |
| 1 | Partial payment |
| 2 | Overpayment |

## Cancel Invoice

### Request

```json
{
  "externalReference": "INV-2024-001"
}
```

### Response

```json
{
  "rescode": "200",
  "resmsg": "success"
}
```

## Implementation Guide

### 1. Create DTOs

```java
// bill/BillManagerOnboardRequest.java
package com.github.daraja.dto.bill;

import com.fasterxml.jackson.annotation.JsonProperty;

public record BillManagerOnboardRequest(
    @JsonProperty("shortcode") String shortcode,
    @JsonProperty("email") String email,
    @JsonProperty("officialContact") String officialContact,
    @JsonProperty("sendReminders") String sendReminders,
    @JsonProperty("logo") String logo,
    @JsonProperty("callbackUrl") String callbackUrl
) {}

// bill/InvoiceRequest.java
public record InvoiceRequest(
    @JsonProperty("externalReference") String externalReference,
    @JsonProperty("billedFullName") String billedFullName,
    @JsonProperty("billedPhoneNumber") String billedPhoneNumber,
    @JsonProperty("billedPeriod") String billedPeriod,
    @JsonProperty("invoiceName") String invoiceName,
    @JsonProperty("dueDate") String dueDate,
    @JsonProperty("accountReference") String accountReference,
    @JsonProperty("amount") String amount,
    @JsonProperty("invoiceItems") List<InvoiceItem> invoiceItems
) {}

public record InvoiceItem(
    @JsonProperty("itemName") String itemName,
    @JsonProperty("amount") String amount
) {}

// bill/InvoiceResponse.java
public record InvoiceResponse(
    @JsonProperty("rescode") String resCode,
    @JsonProperty("resmsg") String resMsg,
    @JsonProperty("Status_Message") String statusMessage
) {
    public boolean isSuccessful() {
        return "200".equals(resCode);
    }
}

// bill/InvoicePaymentCallback.java
public record InvoicePaymentCallback(
    @JsonProperty("TransactionType") String transactionType,
    @JsonProperty("TransID") String transId,
    @JsonProperty("TransTime") String transTime,
    @JsonProperty("TransAmount") String transAmount,
    @JsonProperty("BusinessShortCode") String businessShortCode,
    @JsonProperty("BillRefNumber") String billRefNumber,
    @JsonProperty("InvoiceNumber") String invoiceNumber,
    @JsonProperty("ThirdPartyTransID") String thirdPartyTransId,
    @JsonProperty("MSISDN") String msisdn,
    @JsonProperty("FirstName") String firstName,
    @JsonProperty("MiddleName") String middleName,
    @JsonProperty("LastName") String lastName,
    @JsonProperty("paidInFullFlag") String paidInFullFlag,
    @JsonProperty("paymentMode") String paymentMode
) {
    public boolean isPaidInFull() {
        return "1".equals(paidInFullFlag);
    }

    public boolean isPartialPayment() {
        return "1".equals(paymentMode);
    }

    public boolean isOverpayment() {
        return "2".equals(paymentMode);
    }
}

// bill/CancelInvoiceRequest.java
public record CancelInvoiceRequest(
    @JsonProperty("externalReference") String externalReference
) {}
```

### 2. Create Client

```java
// client/DarajaBillClient.java
package com.github.daraja.client;

import com.github.daraja.autoconfig.DarajaProperties;
import com.github.daraja.dto.bill.*;
import com.github.daraja.exception.DarajaApiException;
import com.github.daraja.util.DarajaUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.List;

public class DarajaBillClient {

    private final RestClient restClient;
    private final DarajaAuthClient authClient;
    private final DarajaProperties properties;

    public DarajaBillClient(RestClient.Builder builder, DarajaAuthClient authClient, DarajaProperties properties) {
        this.authClient = authClient;
        this.properties = properties;
        this.restClient = builder
                .baseUrl(properties.getEnvironment().getBaseUrl())
                .build();
    }

    public InvoiceResponse onboard(String email, String phone, String callbackUrl, boolean sendReminders) {
        BillManagerOnboardRequest request = new BillManagerOnboardRequest(
            properties.getBusinessShortCode(),
            email,
            DarajaUtils.sanitizePhoneNumber(phone),
            sendReminders ? "1" : "0",
            null,
            callbackUrl
        );

        try {
            return restClient.post()
                .uri("/v1/billmanager-invoice/optin")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + authClient.getAccessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(InvoiceResponse.class);
        } catch (Exception e) {
            throw new DarajaApiException("Bill Manager onboarding failed: " + e.getMessage(), e);
        }
    }

    public InvoiceResponse sendInvoice(String invoiceId, String customerName, String customerPhone,
                                        String billingPeriod, String invoiceName, String dueDate,
                                        String accountRef, String amount, List<InvoiceItem> items) {
        InvoiceRequest request = new InvoiceRequest(
            invoiceId,
            customerName,
            DarajaUtils.sanitizePhoneNumber(customerPhone),
            billingPeriod,
            invoiceName,
            dueDate,
            accountRef,
            amount,
            items
        );

        try {
            return restClient.post()
                .uri("/v1/billmanager-invoice/single-invoicing")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + authClient.getAccessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(InvoiceResponse.class);
        } catch (Exception e) {
            throw new DarajaApiException("Invoice sending failed: " + e.getMessage(), e);
        }
    }

    public InvoiceResponse cancelInvoice(String invoiceId) {
        CancelInvoiceRequest request = new CancelInvoiceRequest(invoiceId);

        try {
            return restClient.post()
                .uri("/v1/billmanager-invoice/cancel-single-invoice")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + authClient.getAccessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(InvoiceResponse.class);
        } catch (Exception e) {
            throw new DarajaApiException("Invoice cancellation failed: " + e.getMessage(), e);
        }
    }
}
```

### 3. Create Callback Controller

```java
@RestController
@RequestMapping("/api/v1/mpesa/bill")
public class BillManagerCallbackController {

    @PostMapping("/payment")
    public ResponseEntity<Void> handlePayment(@RequestBody InvoicePaymentCallback callback) {
        String invoiceId = callback.invoiceNumber();
        String transactionId = callback.transId();

        if (callback.isPaidInFull()) {
            // Mark invoice as paid
            // Fulfill order/service
        } else if (callback.isPartialPayment()) {
            // Update invoice with partial payment
            // Adjust remaining balance
        } else if (callback.isOverpayment()) {
            // Process refund or credit balance
        }

        return ResponseEntity.ok().build();
    }
}
```

## Use Cases

1. **Utility Companies**: Send monthly bills for water, electricity
2. **Schools**: Tuition fee invoices with payment tracking
3. **Subscriptions**: Recurring subscription invoices
4. **Healthcare**: Patient billing with itemized charges
5. **Property Management**: Rent and maintenance invoices

## Best Practices

1. **Unique References**: Use unique externalReference for each invoice
2. **Due Dates**: Set reasonable due dates (7-30 days)
3. **Reminders**: Enable automatic payment reminders
4. **Reconciliation**: Run daily reconciliation to catch missed callbacks
5. **Partial Payments**: Handle partial payments gracefully

## Configuration

```yaml
daraja:
  environment: SANDBOX
  consumer-key: ${DARAJA_CONSUMER_KEY}
  consumer-secret: ${DARAJA_CONSUMER_SECRET}
  business-short-code: "600000"
```

## Important Notes

- Onboarding is required once per shortcode
- Customers receive SMS notifications for invoices
- Payment reminders are sent automatically if enabled
- Invoices can be cancelled before payment
- Support both full and partial payments
