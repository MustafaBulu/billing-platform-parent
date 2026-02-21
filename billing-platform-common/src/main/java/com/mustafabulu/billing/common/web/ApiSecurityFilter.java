package com.mustafabulu.billing.common.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mustafabulu.billing.common.tenant.TenantContextFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class ApiSecurityFilter extends OncePerRequestFilter {

    private final ObjectMapper objectMapper;

    @Value("${platform.security.auth.mode:none}")
    private String authMode;

    @Value("${platform.security.api-key.value:}")
    private String expectedApiKey;

    @Value("${platform.security.bearer.tokens:}")
    private String bearerTokens;

    @Value("${platform.security.tenant-guard.enabled:false}")
    private boolean tenantGuardEnabled;

    public ApiSecurityFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        if (isPublicPath(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        if (!authorize(request, response)) {
            return;
        }

        if (tenantGuardEnabled && path.startsWith("/api/v1/")) {
            String tenantHeader = request.getHeader(TenantContextFilter.TENANT_HEADER);
            if (tenantHeader == null || tenantHeader.isBlank()) {
                writeError(response, request, HttpStatus.BAD_REQUEST, "TENANT_HEADER_REQUIRED",
                        "X-Tenant-Id header is required");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private boolean authorize(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String mode = authMode == null ? "none" : authMode.trim().toLowerCase();
        switch (mode) {
            case "none":
                return true;
            case "api-key":
                String apiKey = request.getHeader("X-API-Key");
                if (apiKey == null || apiKey.isBlank() || !apiKey.equals(expectedApiKey)) {
                    writeError(response, request, HttpStatus.UNAUTHORIZED, "AUTH_INVALID_API_KEY",
                            "Missing or invalid API key");
                    return false;
                }
                return true;
            case "bearer":
                String authorization = request.getHeader("Authorization");
                if (authorization == null || !authorization.startsWith("Bearer ")) {
                    writeError(response, request, HttpStatus.UNAUTHORIZED, "AUTH_BEARER_REQUIRED",
                            "Missing bearer token");
                    return false;
                }
                String token = authorization.substring("Bearer ".length()).trim();
                Set<String> allowedTokens = parseAllowedTokens();
                if (allowedTokens.isEmpty() || !allowedTokens.contains(token)) {
                    writeError(response, request, HttpStatus.UNAUTHORIZED, "AUTH_INVALID_BEARER",
                            "Invalid bearer token");
                    return false;
                }
                return true;
            default:
                writeError(response, request, HttpStatus.INTERNAL_SERVER_ERROR, "AUTH_MODE_INVALID",
                        "Unsupported auth mode: " + authMode);
                return false;
        }
    }

    private Set<String> parseAllowedTokens() {
        if (bearerTokens == null || bearerTokens.isBlank()) {
            return Set.of();
        }
        Set<String> allowed = new HashSet<>();
        Arrays.stream(bearerTokens.split(","))
                .map(String::trim)
                .filter(token -> !token.isBlank())
                .forEach(allowed::add);
        return allowed;
    }

    private boolean isPublicPath(String path) {
        return path.startsWith("/actuator")
                || path.startsWith("/swagger-ui")
                || path.startsWith("/v3/api-docs")
                || path.startsWith("/api/v1/system");
    }

    private void writeError(HttpServletResponse response,
                            HttpServletRequest request,
                            HttpStatus status,
                            String code,
                            String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ApiErrorResponse body = new ApiErrorResponse(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                code,
                message,
                request.getRequestURI(),
                getRequestId(request),
                Map.of()
        );
        objectMapper.writeValue(response.getOutputStream(), body);
    }

    private String getRequestId(HttpServletRequest request) {
        Object requestId = request.getAttribute(RequestCorrelationFilter.REQUEST_ID_ATTR);
        return requestId == null ? null : requestId.toString();
    }
}
