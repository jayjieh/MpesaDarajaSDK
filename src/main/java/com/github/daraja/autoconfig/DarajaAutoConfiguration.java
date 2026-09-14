package com.github.daraja.autoconfig;

import com.github.daraja.client.DarajaAuthClient;
import com.github.daraja.client.DarajaStkClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestClient;

@AutoConfiguration
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
}
