package com.github.daraja.autoconfig;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "daraja")
public class DarajaProperties {

    public enum Environment {
        SANDBOX("https://sandbox.safaricom.co.ke"),
        PRODUCTION("https://api.safaricom.co.ke");

        private final String baseUrl;

        Environment(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getBaseUrl() {
            return baseUrl;
        }
    }

    private Environment environment = Environment.SANDBOX;
    private String consumerKey;
    private String consumerSecret;
    private String businessShortCode;
    private String passkey;
    private String defaultCallbackUrl;
    private String initiatorName;
    private String initiatorPassword;

    public Environment getEnvironment() {
        return environment;
    }

    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    public String getConsumerKey() {
        return consumerKey;
    }

    public void setConsumerKey(String consumerKey) {
        this.consumerKey = consumerKey;
    }

    public String getConsumerSecret() {
        return consumerSecret;
    }

    public void setConsumerSecret(String consumerSecret) {
        this.consumerSecret = consumerSecret;
    }

    public String getBusinessShortCode() {
        return businessShortCode;
    }

    public void setBusinessShortCode(String businessShortCode) {
        this.businessShortCode = businessShortCode;
    }

    public String getPasskey() {
        return passkey;
    }

    public void setPasskey(String passkey) {
        this.passkey = passkey;
    }

    public String getDefaultCallbackUrl() {
        return defaultCallbackUrl;
    }

    public void setDefaultCallbackUrl(String defaultCallbackUrl) {
        this.defaultCallbackUrl = defaultCallbackUrl;
    }

    public String getInitiatorName() {
        return initiatorName;
    }

    public void setInitiatorName(String initiatorName) {
        this.initiatorName = initiatorName;
    }

    public String getInitiatorPassword() {
        return initiatorPassword;
    }

    public void setInitiatorPassword(String initiatorPassword) {
        this.initiatorPassword = initiatorPassword;
    }
}
