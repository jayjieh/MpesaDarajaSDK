# M-Pesa Express (STK Push) API

**Status: Implemented**

Lipa Na M-Pesa Online API initiates a payment prompt on the customer's phone.

## Endpoints

| Operation | Method | Endpoint |
|-----------|--------|----------|
| Initiate STK Push | POST | `/mpesa/stkpush/v1/processrequest` |
| Query STK Status | POST | `/mpesa/stkpushquery/v1/query` |

## STK Push Request

### Request Parameters

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| BusinessShortCode | String | Yes | Organization shortcode (Paybill/Till) |
| Password | String | Yes | Base64(ShortCode + Passkey + Timestamp) |
| Timestamp | String | Yes | Format: `YYYYMMDDHHmmss` |
| TransactionType | String | Yes | `CustomerPayBillOnline` or `CustomerBuyGoodsOnline` |
| Amount | String | Yes | Transaction amount (whole numbers only) |
| PartyA | String | Yes | Customer phone number (254XXXXXXXXX) |
| PartyB | String | Yes | Organization shortcode |
| PhoneNumber | String | Yes | Customer phone number (254XXXXXXXXX) |
| CallBackURL | String | Yes | HTTPS callback URL |
| AccountReference | String | Yes | Account/Order reference (max 12 chars) |
| TransactionDesc | String | Yes | Transaction description (max 13 chars) |

### Request Example

```json
{
  "BusinessShortCode": "174379",
  "Password": "MTc0Mzc5YmZiMjc5ZjlhYTliZGJjZjE1OGU5N2RkNzFhNDY3Y2QyZTBjODkzMDU5YjEwZjc4ZTZiNzJhZGExZWQyYzkxOTIwMjMwODE1MTIzMDU2",
  "Timestamp": "20230815123056",
  "TransactionType": "CustomerPayBillOnline",
  "Amount": "1",
  "PartyA": "254712345678",
  "PartyB": "174379",
  "PhoneNumber": "254712345678",
  "CallBackURL": "https://example.com/callback",
  "AccountReference": "ORDER123",
  "TransactionDesc": "Payment"
}
```

### Response

```json
{
  "MerchantRequestID": "29115-34620561-1",
  "CheckoutRequestID": "ws_CO_191220191020363925",
  "ResponseCode": "0",
  "ResponseDescription": "Success. Request accepted for processing",
  "CustomerMessage": "Success. Request accepted for processing"
}
```

## STK Push Callback

Safaricom sends payment result to your CallBackURL:

### Successful Payment

```json
{
  "Body": {
    "stkCallback": {
      "MerchantRequestID": "29115-34620561-1",
      "CheckoutRequestID": "ws_CO_191220191020363925",
      "ResultCode": 0,
      "ResultDesc": "The service request is processed successfully.",
      "CallbackMetadata": {
        "Item": [
          { "Name": "Amount", "Value": 1.00 },
          { "Name": "MpesaReceiptNumber", "Value": "NLJ7RT61SV" },
          { "Name": "TransactionDate", "Value": 20191219102115 },
          { "Name": "PhoneNumber", "Value": 254712345678 }
        ]
      }
    }
  }
}
```

### Failed/Cancelled Payment

```json
{
  "Body": {
    "stkCallback": {
      "MerchantRequestID": "29115-34620561-1",
      "CheckoutRequestID": "ws_CO_191220191020363925",
      "ResultCode": 1032,
      "ResultDesc": "Request cancelled by user"
    }
  }
}
```

### Common Result Codes

| Code | Description |
|------|-------------|
| 0 | Success |
| 1 | Insufficient balance |
| 1032 | Cancelled by user |
| 1037 | Timeout waiting for user input |
| 2001 | Wrong PIN entered |

## STK Push Query

Query status if callback is delayed.

### Request

```json
{
  "BusinessShortCode": "174379",
  "Password": "Base64EncodedPassword",
  "Timestamp": "20230815123056",
  "CheckoutRequestID": "ws_CO_191220191020363925"
}
```

### Response

```json
{
  "ResponseCode": "0",
  "ResponseDescription": "The service request has been accepted successfully",
  "MerchantRequestID": "29115-34620561-1",
  "CheckoutRequestID": "ws_CO_191220191020363925",
  "ResultCode": "0",
  "ResultDesc": "The service request is processed successfully."
}
```

## Existing Implementation

### Client: `DarajaStkClient.java`

```java
// Initiate STK Push
StkPushResponse response = stkClient.sendStkPush(
    "0712345678",      // phone (auto-sanitized)
    "100",             // amount
    "ORDER123",        // account reference
    "Payment for order"  // description
);

// Query status
StkQueryResponse status = stkClient.queryStatus(response.checkoutRequestId());
```

### DTOs

- `StkPushRequest` - Request payload
- `StkPushResponse` - API response
- `StkQueryRequest` - Query payload
- `StkQueryResponse` - Query response
- `StkCallbackWrapper` - Callback payload

## Configuration

```yaml
daraja:
  environment: SANDBOX
  consumer-key: ${DARAJA_CONSUMER_KEY}
  consumer-secret: ${DARAJA_CONSUMER_SECRET}
  business-short-code: "174379"
  passkey: "bfb279f9aa9bdbcf158e97dd71a467cd2e0c893059b10f78e6b72ada1ed2c919"
  default-callback-url: "https://yourdomain.com/api/v1/mpesa/stk-callback"
```

## Sandbox Test Credentials

| Field | Value |
|-------|-------|
| BusinessShortCode | 174379 |
| Passkey | bfb279f9aa9bdbcf158e97dd71a467cd2e0c893059b10f78e6b72ada1ed2c919 |
| Test Phone | 254708374149 |
