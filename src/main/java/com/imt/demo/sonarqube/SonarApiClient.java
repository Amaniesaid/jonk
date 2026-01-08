package com.imt.demo.sonarqube;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.StringJoiner;

@Slf4j
@Component
public class SonarApiClient {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final SonarQubeProperties properties;

    public SonarApiClient(SonarQubeProperties properties) {
        this.properties = properties;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    public JsonNode getJson(String path, Map<String, String> queryParams) {
        try {
            URI uri = buildUri(path, queryParams);
            HttpRequest request = baseRequest(uri)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new IllegalStateException("SonarQube API GET failed: " + response.statusCode() + " - " + response.body());
            }
            return objectMapper.readTree(response.body());
        } catch (Exception e) {
            throw new IllegalStateException("SonarQube API GET error: " + path + " - " + e.getMessage(), e);
        }
    }

    public JsonNode postForm(String path, Map<String, String> formParams) {
        try {
            URI uri = buildUri(path, Map.of());
            String body = encodeForm(formParams);

            HttpRequest request = baseRequest(uri)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new IllegalStateException("SonarQube API POST failed: " + response.statusCode() + " - " + response.body());
            }
            return objectMapper.readTree(response.body());
        } catch (Exception e) {
            throw new IllegalStateException("SonarQube API POST error: " + path + " - " + e.getMessage(), e);
        }
    }

    private HttpRequest.Builder baseRequest(URI uri) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(20));

        String token = properties.getToken();
        if (token != null && !token.isBlank()) {
            // SonarQube token auth: Basic base64(token:)
            String basic = Base64.getEncoder().encodeToString((token + ":").getBytes(StandardCharsets.UTF_8));
            builder.header("Authorization", "Basic " + basic);
        }

        return builder;
    }

    private URI buildUri(String path, Map<String, String> queryParams) {
        String base = properties.getHostUrl();
        if (base == null || base.isBlank()) {
            throw new IllegalStateException("SonarQube hostUrl is not configured");
        }

        StringBuilder sb = new StringBuilder();
        sb.append(base);
        if (!base.endsWith("/") && !path.startsWith("/")) {
            sb.append('/');
        }
        if (base.endsWith("/") && path.startsWith("/")) {
            sb.append(path.substring(1));
        } else {
            sb.append(path);
        }

        if (queryParams != null && !queryParams.isEmpty()) {
            sb.append('?');
            StringJoiner joiner = new StringJoiner("&");
            for (Map.Entry<String, String> entry : queryParams.entrySet()) {
                joiner.add(urlEncode(entry.getKey()) + "=" + urlEncode(entry.getValue()));
            }
            sb.append(joiner);
        }

        return URI.create(sb.toString());
    }

    private String encodeForm(Map<String, String> formParams) {
        if (formParams == null || formParams.isEmpty()) {
            return "";
        }
        StringJoiner joiner = new StringJoiner("&");
        for (Map.Entry<String, String> entry : formParams.entrySet()) {
            joiner.add(urlEncode(entry.getKey()) + "=" + urlEncode(entry.getValue()));
        }
        return joiner.toString();
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
