package com.smartstudy.planning.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;

/**
 * Interceptor that logs outgoing Gemini REST requests and responses.
 * <ul>
 *   <li>Always logs the request URL and response status at INFO level.</li>
 *   <li>Logs a masked API key to confirm authentication is present.</li>
 *   <li>Logs full headers and body at DEBUG level for deeper diagnostics.</li>
 *   <li>Logs an ERROR if the {@code x-goog-api-key} header is missing or empty.</li>
 * </ul>
 */
public class GeminiRequestLoggingInterceptor implements ClientHttpRequestInterceptor {

    private static final Logger log = LoggerFactory.getLogger(GeminiRequestLoggingInterceptor.class);

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body,
                                         ClientHttpRequestExecution execution) throws IOException {

        logRequest(request);

        ClientHttpResponse response = execution.execute(request, body);

        logResponse(request, response);

        return response;
    }

    private void logRequest(HttpRequest request) {
        log.info("Gemini request: {} {}", request.getMethod(), request.getURI());

        // Verify API key header is present and non-empty
        String apiKeyHeader = request.getHeaders().getFirst("x-goog-api-key");
        if (apiKeyHeader == null) {
            log.error("Gemini request MISSING x-goog-api-key header — will get 403");
        } else if (apiKeyHeader.isBlank()) {
            log.error("Gemini request has EMPTY x-goog-api-key header — will get 403");
        } else {
            String masked = apiKeyHeader.substring(0, Math.min(8, apiKeyHeader.length()))
                    + "..."
                    + apiKeyHeader.substring(Math.max(0, apiKeyHeader.length() - 4));
            log.info("Gemini request authenticated — key: {} (length={})", masked, apiKeyHeader.length());
        }

        // Log all headers at DEBUG level for deeper diagnostics
        if (log.isDebugEnabled()) {
            request.getHeaders().forEach((name, values) -> {
                if ("x-goog-api-key".equalsIgnoreCase(name)) {
                    log.debug("  Header: {} = [MASKED]", name);
                } else {
                    log.debug("  Header: {} = {}", name, values);
                }
            });
        }
    }

    private void logResponse(HttpRequest request, ClientHttpResponse response) throws IOException {
        int status = response.getStatusCode().value();

        if (status >= 400) {
            log.error("Gemini response: {} {} — {} {}",
                    request.getMethod(), request.getURI(),
                    status, response.getStatusText());
        } else {
            log.info("Gemini response: {} {} — {} {}",
                    request.getMethod(), request.getURI(),
                    status, response.getStatusText());
        }
    }
}
