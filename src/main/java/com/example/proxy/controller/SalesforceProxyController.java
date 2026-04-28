package com.example.proxy.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.Enumeration;
import java.util.Set;

@RestController
@RequestMapping("/sf-api")
public class SalesforceProxyController {

    private static final Logger logger = LoggerFactory.getLogger(SalesforceProxyController.class);

    @Value("${salesforce.instance-url}")
    private String instanceUrl;

    @Value("${heroku.base-url}")
    private String herokuBaseUrl;

    private static final Set<String> STRIP_HEADERS = Set.of(
            "x-forwarded-for",
            "x-real-ip",
            "x-device-id",
            "x-app-version",
            "host",
            "connection");

    private final RestTemplate restTemplate = buildRestTemplate();

    @RequestMapping("/**")
    public ResponseEntity<String> proxy(
            HttpServletRequest request,
            @RequestBody(required = false) String body) {

        try {
            // Log incoming request details
            logger.info("=== Incoming Request ===");
            logger.info("Method: {}", request.getMethod());
            logger.info("URI: {}", request.getRequestURI());
            logger.info("Query: {}", request.getQueryString());

            Enumeration<String> headerNames = request.getHeaderNames();
            while (headerNames.hasMoreElements()) {
                String headerName = headerNames.nextElement();
                // Careful not to log sensitive headers fully in a real app, but as requested logging everything here
                logger.info("Header [{}]: {}", headerName, request.getHeader(headerName));
            }
            logger.info("Body: {}", body);

            // Build SF URL
            String sfPath = request.getRequestURI().replace("/sf-api", "");
            String sfUrl = instanceUrl + sfPath;
            if (request.getQueryString() != null) {
                sfUrl += "?" + request.getQueryString();
            }

            // Forward mobile's SF token directly — just clean the headers
            HttpHeaders sfHeaders = new HttpHeaders();
            sfHeaders.set("Authorization", request.getHeader("Authorization")); // mobile's token
            sfHeaders.set("Content-Type", "application/json");
            sfHeaders.set("Accept", "application/json");
            sfHeaders.set("User-Agent", "LnTRealty/1.0"); // fixed UA
            sfHeaders.set("Connection", "keep-alive");

            HttpMethod method = HttpMethod.valueOf(request.getMethod());
            HttpEntity<String> entity = new HttpEntity<>(body, sfHeaders);

            // Log outgoing request details
            logger.info("=== Outgoing Request to Salesforce ===");
            logger.info("URL: {}", sfUrl);
            logger.info("Method: {}", method);
            logger.info("Headers: {}", sfHeaders);
            logger.info("Body: {}", body);

            ResponseEntity<String> sfResponse = restTemplate.exchange(
                    sfUrl, method, entity, String.class);

            // Log response from Salesforce
            logger.info("=== Response from Salesforce ===");
            logger.info("Status Code: {}", sfResponse.getStatusCode());
            logger.info("Headers: {}", sfResponse.getHeaders());
            logger.info("Body: {}", sfResponse.getBody());

            return ResponseEntity
                    .status(sfResponse.getStatusCode())
                    .body(sfResponse.getBody());

        } catch (HttpClientErrorException | HttpServerErrorException ex) {
            logger.error("=== Error Response from Salesforce ===");
            logger.error("Status Code: {}", ex.getStatusCode());
            logger.error("Response Body: {}", ex.getResponseBodyAsString());
            return ResponseEntity
                    .status(ex.getStatusCode())
                    .body(ex.getResponseBodyAsString());

        } catch (Exception ex) {
            logger.error("=== Proxy Exception ===", ex);
            return ResponseEntity
                    .status(HttpStatus.BAD_GATEWAY)
                    .body("{\"error\": \"Proxy error: " + ex.getMessage() + "\"}");
        }
    }


    @RequestMapping("/heroku-api/**")
    public ResponseEntity<String> proxyHeroku(
            HttpServletRequest request,
            @RequestBody(required = false) String body) {

        String herokuPath = request.getRequestURI().replace("/heroku-api", "");
        String targetUrl = buildTargetUrl(herokuBaseUrl, herokuPath, request.getQueryString());

        return forward(request, body, targetUrl);
    }


    private ResponseEntity<String> forward(
            HttpServletRequest request,
            String body,
            String targetUrl) {

        try {
            String authHeader = request.getHeader("Authorization");
            if (authHeader == null || authHeader.isBlank()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body("{\"error\": \"" + "Missing Authorization header" + "\"}");
            }

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", authHeader);
            headers.set("Content-Type", "application/json");
            headers.set("Accept", "application/json");
            headers.set("User-Agent", "MyCompanyIntegration/1.0");
            headers.set("Connection", "keep-alive");

            HttpMethod method = HttpMethod.valueOf(request.getMethod());
            HttpEntity<String> entity = new HttpEntity<>(body, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    targetUrl, method, entity, String.class
            );

            return ResponseEntity
                    .status(response.getStatusCode())
                    .body(response.getBody());

        } catch (HttpClientErrorException | HttpServerErrorException ex) {
            return ResponseEntity
                    .status(ex.getStatusCode())
                    .body(ex.getResponseBodyAsString());

        } catch (Exception ex) {
            return ResponseEntity
                    .status(HttpStatus.BAD_GATEWAY)
                    .body("{\"error\": \"Proxy error: " + ex.getMessage() + "\"}");
        }
    }


    private String buildTargetUrl(String base, String path, String queryString) {
        String url = base.stripTrailing() + path;
        if (queryString != null) url += "?" + queryString;
        return url;
    }


    private RestTemplate buildRestTemplate() {
        HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory();
        factory.setConnectionRequestTimeout(60000);
        factory.setReadTimeout(60000);
        return new RestTemplate(factory);
    }
}