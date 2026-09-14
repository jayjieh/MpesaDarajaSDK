# Shared Components

Common DTOs and utilities used across multiple Daraja APIs.

## ResultParameters DTO

Many APIs return results in a `ResultParameters` structure with a list of key-value pairs.

```java
// dto/common/ResultParameters.java
package com.github.daraja.dto.common;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public record ResultParameters(
    @JsonProperty("ResultParameter") List<ResultParameter> resultParameter
) {
    public String getParameterValue(String key) {
        if (resultParameter == null) return null;
        return resultParameter.stream()
            .filter(p -> key.equals(p.key()))
            .map(p -> String.valueOf(p.value()))
            .findFirst()
            .orElse(null);
    }

    public Map<String, Object> toMap() {
        if (resultParameter == null) return Map.of();
        return resultParameter.stream()
            .collect(Collectors.toMap(
                ResultParameter::key,
                ResultParameter::value,
                (v1, v2) -> v2  // Handle duplicates
            ));
    }
}

public record ResultParameter(
    @JsonProperty("Key") String key,
    @JsonProperty("Value") Object value
) {}
```

## Security Credential Utility

B2C, B2B, Transaction Status, Account Balance, Reversal, and Tax Remittance APIs require encrypted initiator credentials.

### Certificate Files

Download from [Daraja Portal](https://developer.safaricom.co.ke/APIs/Authorization):

```
src/main/resources/certs/
├── SandboxCertificate.cer
└── ProductionCertificate.cer
```

### Encryption Implementation

```java
// util/DarajaUtils.java - Add these methods

import javax.crypto.Cipher;
import java.io.InputStream;
import java.security.PublicKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Base64;

public class DarajaUtils {

    // ... existing methods ...

    /**
     * Encrypt initiator password using Safaricom's certificate
     */
    public static String encryptSecurityCredential(String password, DarajaProperties.Environment environment) {
        try {
            String certPath = environment == DarajaProperties.Environment.SANDBOX
                ? "certs/SandboxCertificate.cer"
                : "certs/ProductionCertificate.cer";

            InputStream certStream = DarajaUtils.class.getClassLoader().getResourceAsStream(certPath);
            if (certStream == null) {
                throw new DarajaApiException("Certificate not found: " + certPath +
                    ". Download from Daraja portal and place in src/main/resources/certs/");
            }

            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            X509Certificate certificate = (X509Certificate) cf.generateCertificate(certStream);
            PublicKey publicKey = certificate.getPublicKey();

            Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            cipher.init(Cipher.ENCRYPT_MODE, publicKey);
            byte[] encrypted = cipher.doFinal(password.getBytes(StandardCharsets.UTF_8));

            return Base64.getEncoder().encodeToString(encrypted);
        } catch (DarajaApiException e) {
            throw e;
        } catch (Exception e) {
            throw new DarajaApiException("Failed to encrypt security credential: " + e.getMessage(), e);
        }
    }
}
```

## Phone Number Sanitization

All M-Pesa APIs require phone numbers in format `254XXXXXXXXX`.

```java
// util/DarajaUtils.java - This should already exist

/**
 * Normalize phone number to 254XXXXXXXXX format
 * Handles: 0712345678, +254712345678, 254712345678, 712345678
 */
public static String sanitizePhoneNumber(String phone) {
    if (phone == null || phone.isBlank()) {
        throw new DarajaApiException("Phone number cannot be empty");
    }

    // Remove all non-digits
    String digits = phone.replaceAll("[^0-9]", "");

    // Handle different formats
    if (digits.startsWith("254") && digits.length() == 12) {
        return digits;  // Already correct format
    } else if (digits.startsWith("0") && digits.length() == 10) {
        return "254" + digits.substring(1);  // 0712... -> 254712...
    } else if (digits.startsWith("7") && digits.length() == 9) {
        return "254" + digits;  // 712... -> 254712...
    } else if (digits.startsWith("1") && digits.length() == 9) {
        return "254" + digits;  // 112... -> 254112... (Safaricom Home)
    } else {
        throw new DarajaApiException("Invalid phone number format: " + phone);
    }
}
```

## Timestamp Generation

STK Push requires timestamps in format `YYYYMMDDHHmmss`.

```java
// util/DarajaUtils.java - This should already exist

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

private static final DateTimeFormatter TIMESTAMP_FORMAT =
    DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

/**
 * Generate timestamp in Daraja format
 */
public static String getTimestamp() {
    return LocalDateTime.now().format(TIMESTAMP_FORMAT);
}

/**
 * Generate STK password: Base64(ShortCode + Passkey + Timestamp)
 */
public static String generateStkPassword(String shortCode, String passkey, String timestamp) {
    String data = shortCode + passkey + timestamp;
    return Base64.getEncoder().encodeToString(data.getBytes(StandardCharsets.UTF_8));
}
```

## Auto-Configuration Updates

To register all clients, update the auto-configuration:

```java
// autoconfig/DarajaAutoConfiguration.java
package com.github.daraja.autoconfig;

import com.github.daraja.client.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(DarajaProperties.class)
public class DarajaAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public DarajaAuthClient darajaAuthClient(RestClient.Builder builder, DarajaProperties properties) {
        return new DarajaAuthClient(builder, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public DarajaStkClient darajaStkClient(RestClient.Builder builder, DarajaAuthClient authClient, DarajaProperties properties) {
        return new DarajaStkClient(builder, authClient, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public DarajaC2BClient darajaC2BClient(RestClient.Builder builder, DarajaAuthClient authClient, DarajaProperties properties) {
        return new DarajaC2BClient(builder, authClient, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public DarajaB2CClient darajaB2CClient(RestClient.Builder builder, DarajaAuthClient authClient, DarajaProperties properties) {
        return new DarajaB2CClient(builder, authClient, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public DarajaB2BClient darajaB2BClient(RestClient.Builder builder, DarajaAuthClient authClient, DarajaProperties properties) {
        return new DarajaB2BClient(builder, authClient, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public DarajaQueryClient darajaQueryClient(RestClient.Builder builder, DarajaAuthClient authClient, DarajaProperties properties) {
        return new DarajaQueryClient(builder, authClient, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public DarajaReversalClient darajaReversalClient(RestClient.Builder builder, DarajaAuthClient authClient, DarajaProperties properties) {
        return new DarajaReversalClient(builder, authClient, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public DarajaTaxClient darajaTaxClient(RestClient.Builder builder, DarajaAuthClient authClient, DarajaProperties properties) {
        return new DarajaTaxClient(builder, authClient, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public DarajaQRClient darajaQRClient(RestClient.Builder builder, DarajaAuthClient authClient, DarajaProperties properties) {
        return new DarajaQRClient(builder, authClient, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public DarajaBillClient darajaBillClient(RestClient.Builder builder, DarajaAuthClient authClient, DarajaProperties properties) {
        return new DarajaBillClient(builder, authClient, properties);
    }
}
```

## Full Configuration Example

```yaml
daraja:
  environment: SANDBOX  # or PRODUCTION
  consumer-key: ${DARAJA_CONSUMER_KEY}
  consumer-secret: ${DARAJA_CONSUMER_SECRET}
  business-short-code: "174379"
  passkey: "bfb279f9aa9bdbcf158e97dd71a467cd2e0c893059b10f78e6b72ada1ed2c919"
  default-callback-url: "https://yourdomain.com/api/v1/mpesa/callback"
  initiator-name: "testapi"
  initiator-password: ${DARAJA_INITIATOR_PASSWORD}
```

## Error Handling

```java
// exception/DarajaApiException.java - This should already exist
package com.github.daraja.exception;

public class DarajaApiException extends RuntimeException {

    public DarajaApiException(String message) {
        super(message);
    }

    public DarajaApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

## Global Exception Handler

```java
@RestControllerAdvice
public class DarajaExceptionHandler {

    @ExceptionHandler(DarajaApiException.class)
    public ResponseEntity<Map<String, String>> handleDarajaException(DarajaApiException e) {
        return ResponseEntity.badRequest().body(Map.of(
            "error", "Daraja API Error",
            "message", e.getMessage()
        ));
    }
}
```
