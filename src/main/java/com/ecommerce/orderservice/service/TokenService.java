package com.ecommerce.orderservice.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Service
public class TokenService {
    private final RestTemplate restTemplate;
    private final String tokenUrl;
    private final String clientId;
    private final String clientSecret;

    public TokenService(
            RestTemplate restTemplate,
            @Value("${spring.security.oauth2.client.provider.keycloak.token-uri}") String tokenUrl,
            @Value("${spring.security.oauth2.client.registration.order-service-client.client-id}") String clientId,
            @Value("${spring.security.oauth2.client.registration.order-service-client.client-secret}") String clientSecret
    ) {
        this.restTemplate = restTemplate;
        this.tokenUrl = tokenUrl;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    public String getServiceToken() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "client_credentials");
        body.add("client_id", clientId);
        body.add("client_secret", clientSecret);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

        Map<String, Object> response = restTemplate.postForObject(tokenUrl, request, Map.class);
        if (response == null || !response.containsKey("access_token")) {
            throw new IllegalStateException("Failed to obtain service token");
        }

        return "Bearer " + response.get("access_token");
    }
}