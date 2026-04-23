package com.knibel.entraselfservice.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class GraphApiLoggingInterceptor implements ClientHttpRequestInterceptor {

    private static final Logger log = LoggerFactory.getLogger(GraphApiLoggingInterceptor.class);

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        log.info("Graph API request: {} {} body={}",
                request.getMethod(),
                request.getURI(),
                new String(body, StandardCharsets.UTF_8));

        ClientHttpResponse response = execution.execute(request, body);

        byte[] responseBody = response.getBody().readAllBytes();
        log.info("Graph API response: {} body={}",
                response.getStatusCode(),
                new String(responseBody, StandardCharsets.UTF_8));

        return response;
    }
}
