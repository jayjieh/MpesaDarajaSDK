# Daraja M-Pesa API Integration Skill

This skill provides comprehensive guidance for implementing all Safaricom Daraja M-Pesa APIs in the `daraja-spring-boot-starter` SDK.

## Available APIs

| API | Description | Status | Guide |
|-----|-------------|--------|-------|
| **M-Pesa Express (STK Push)** | Merchant-initiated payment prompts | Implemented | [stk-push.md](guides/stk-push.md) |
| **C2B (Customer to Business)** | PayBill/Till payments from M-Pesa menu | Partial | [c2b.md](guides/c2b.md) |
| **B2C (Business to Customer)** | Payouts, salaries, refunds | Not Started | [b2c.md](guides/b2c.md) |
| **B2B (Business to Business)** | Inter-business transfers | Not Started | [b2b.md](guides/b2b.md) |
| **Transaction Status** | Query any transaction status | Not Started | [transaction-status.md](guides/transaction-status.md) |
| **Account Balance** | Check account balance | Not Started | [account-balance.md](guides/account-balance.md) |
| **Reversal** | Reverse completed transactions | Not Started | [reversal.md](guides/reversal.md) |
| **Tax Remittance** | Remit tax to KRA | Not Started | [tax-remittance.md](guides/tax-remittance.md) |
| **Dynamic QR** | Generate payment QR codes | Not Started | [dynamic-qr.md](guides/dynamic-qr.md) |
| **Bill Manager** | Invoice management | Not Started | [bill-manager.md](guides/bill-manager.md) |

## Base URLs

| Environment | URL |
|-------------|-----|
| Sandbox | `https://sandbox.safaricom.co.ke` |
| Production | `https://api.safaricom.co.ke` |

## Authentication

All APIs require OAuth 2.0 Bearer token authentication:

```
GET /oauth/v1/generate?grant_type=client_credentials
Authorization: Basic Base64(ConsumerKey:ConsumerSecret)
```

Token is valid for 3600 seconds (1 hour). The SDK handles this automatically via `DarajaAuthClient`.

## Implementation Patterns

### Client Pattern
Each API should have its own client class:
```
src/main/java/com/github/daraja/client/
├── DarajaAuthClient.java      # OAuth authentication
├── DarajaStkClient.java       # STK Push (existing)
├── DarajaC2BClient.java       # C2B operations
├── DarajaB2CClient.java       # B2C payouts
├── DarajaB2BClient.java       # B2B transfers
├── DarajaQueryClient.java     # Transaction Status, Balance, Reversal
└── DarajaQRClient.java        # QR Code generation
```

### DTO Pattern
Use Java records with Jackson annotations:
```
src/main/java/com/github/daraja/dto/
├── auth/
├── stk/
├── c2b/
├── b2c/
├── b2b/
├── query/
├── reversal/
├── qr/
└── callback/
```

### Security Credential Pattern
B2C, B2B, Transaction Status, Account Balance, and Reversal APIs require encrypted security credentials:
```java
String securityCredential = DarajaUtils.encryptInitiatorPassword(
    initiatorPassword,
    environment  // determines which certificate to use
);
```

## Phone Number Format

All phone numbers must be in format `254XXXXXXXXX`:
- No leading zero
- No plus sign
- Country code 254 (Kenya)

The SDK provides `DarajaUtils.sanitizePhoneNumber()` for automatic normalization.

## Callback Handling

All async APIs require callback URLs:
- Must be HTTPS
- Must return HTTP 200 immediately
- Process results asynchronously

## Reference

- [Safaricom Daraja Portal](https://developer.safaricom.co.ke/)
- [API Documentation](https://developer.safaricom.co.ke/APIs)
