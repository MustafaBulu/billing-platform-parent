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
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
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
    private static final Set<String> NO_SCOPES = Set.of();
    private static final ScopeRule[] SCOPE_RULES = new ScopeRule[]{
            new ScopeRule("POST", "/api/v1/tenants", false, Set.of("tenant:write")),
            new ScopeRule("POST", "/api/v1/usage/events", false, Set.of("usage:write")),
            new ScopeRule("GET", "/api/v1/usage/totals/", true, Set.of("usage:read")),
            new ScopeRule("POST", "/api/v1/billing/rate", false, Set.of("billing:write")),
            new ScopeRule("POST", "/api/v1/invoices/generate", false, Set.of("invoice:write")),
            new ScopeRule("POST", "/api/v1/invoices/generate-and-settle", false, Set.of("invoice:settle")),
            new ScopeRule("GET", "/api/v1/invoices/", true, Set.of("invoice:read")),
            new ScopeRule("POST", "/api/v1/payments/process", false, Set.of("payment:write")),
            new ScopeRule("POST", "/api/v1/settlements/start", false, Set.of("settlement:write")),
            new ScopeRule("GET", "/api/v1/settlements/", true, Set.of("settlement:read"))
    };

    private final ObjectMapper objectMapper;

    @Value("${platform.security.auth.mode:none}")
    private String authMode;

    @Value("${platform.security.api-key.value:}")
    private String expectedApiKey;

    @Value("${platform.security.bearer.tokens:}")
    private String bearerTokens;

    @Value("${platform.security.bearer.token-scopes:}")
    private String bearerTokenScopes;

    @Value("${platform.security.bearer.token-tenants:}")
    private String bearerTokenTenants;

    @Value("${platform.security.authorization.enabled:false}")
    private boolean authorizationEnabled;

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

        AuthContext authContext = authorize(request, response);
        if (authContext == null) {
            return;
        }

        String tenantHeader = normalizedHeaderValue(request, TenantContextFilter.TENANT_HEADER);
        if (tenantGuardEnabled && requiresTenant(path) && tenantHeader == null) {
            writeError(response, request, HttpStatus.BAD_REQUEST, "TENANT_HEADER_REQUIRED",
                    "X-Tenant-Id header is required");
            return;
        }

        if (!authorizeRequest(request, response, path, tenantHeader, authContext)) {
            return;
        }

        filterChain.doFilter(request, response);
    }

    private AuthContext authorize(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String mode = authMode == null ? "none" : authMode.trim().toLowerCase();
        switch (mode) {
            case "none":
                return new AuthContext(mode, Set.of(), Set.of());
            case "api-key":
                String apiKey = normalizedHeaderValue(request, "X-API-Key");
                if (apiKey == null || !apiKey.equals(expectedApiKey)) {
                    writeError(response, request, HttpStatus.UNAUTHORIZED, "AUTH_INVALID_API_KEY",
                            "Missing or invalid API key");
                    return null;
                }
                return new AuthContext(mode, Set.of("*"), Set.of("*"));
            case "bearer":
                String authorization = normalizedHeaderValue(request, "Authorization");
                if (authorization == null || !authorization.startsWith("Bearer ")) {
                    writeError(response, request, HttpStatus.UNAUTHORIZED, "AUTH_BEARER_REQUIRED",
                            "Missing bearer token");
                    return null;
                }
                String token = authorization.substring("Bearer ".length()).trim();
                Set<String> allowedTokens = parseAllowedTokens();
                if (allowedTokens.isEmpty() || !allowedTokens.contains(token)) {
                    writeError(response, request, HttpStatus.UNAUTHORIZED, "AUTH_INVALID_BEARER",
                            "Invalid bearer token");
                    return null;
                }
                Map<String, Set<String>> scopedTokens = parseTokenMappings(bearerTokenScopes);
                Map<String, Set<String>> tenantBoundTokens = parseTokenMappings(bearerTokenTenants);
                Set<String> scopes = scopedTokens.getOrDefault(token, Set.of());
                Set<String> allowedTenants = tenantBoundTokens.getOrDefault(token, Set.of("*"));
                return new AuthContext(mode, scopes, allowedTenants);
            default:
                writeError(response, request, HttpStatus.INTERNAL_SERVER_ERROR, "AUTH_MODE_INVALID",
                        "Unsupported auth mode: " + authMode);
                return null;
        }
    }

    private boolean authorizeRequest(HttpServletRequest request,
                                     HttpServletResponse response,
                                     String path,
                                     String tenantHeader,
                                     AuthContext authContext) throws IOException {
        if (!authorizationEnabled || !path.startsWith("/api/v1/")) {
            return true;
        }

        if ("none".equals(authContext.mode())) {
            writeError(response, request, HttpStatus.INTERNAL_SERVER_ERROR, "AUTHZ_REQUIRES_AUTH",
                    "Authorization requires auth mode api-key or bearer");
            return false;
        }

        if (!hasRequiredScope(authContext, request.getMethod(), path)) {
            writeError(response, request, HttpStatus.FORBIDDEN, "AUTHZ_SCOPE_FORBIDDEN",
                    "Missing required scope for endpoint");
            return false;
        }

        if (tenantHeader != null
                && !authContext.allowedTenants().contains("*")
                && !authContext.allowedTenants().contains(tenantHeader)) {
            writeError(response, request, HttpStatus.FORBIDDEN, "AUTHZ_TENANT_FORBIDDEN",
                    "Token is not allowed for requested tenant");
            return false;
        }

        return true;
    }

    private boolean hasRequiredScope(AuthContext authContext, String method, String path) {
        if (authContext.scopes().contains("*")) {
            return true;
        }
        Set<String> requiredScopes = resolveRequiredScopes(method, path);
        if (requiredScopes.isEmpty()) {
            return true;
        }
        return authContext.scopes().containsAll(requiredScopes);
    }

    private Set<String> resolveRequiredScopes(String method, String path) {
        String normalizedMethod = method == null ? "" : method.trim().toUpperCase();
        String normalizedPath = path == null ? "" : path;
        for (ScopeRule scopeRule : SCOPE_RULES) {
            if (scopeRule.matches(normalizedMethod, normalizedPath)) {
                return scopeRule.requiredScopes();
            }
        }
        return NO_SCOPES;
    }

    private boolean requiresTenant(String path) {
        return path.startsWith("/api/v1/")
                && !path.startsWith("/api/v1/system")
                && !"/api/v1/tenants".equals(path);
    }

    private String normalizedHeaderValue(HttpServletRequest request, String headerName) {
        String raw = request.getHeader(headerName);
        if (raw == null) {
            return null;
        }
        String normalized = raw.trim();
        return normalized.isBlank() ? null : normalized;
    }

    private Map<String, Set<String>> parseTokenMappings(String rawMappings) {
        if (rawMappings == null || rawMappings.isBlank()) {
            return Map.of();
        }

        Map<String, Set<String>> mappedValues = new HashMap<>();
        Arrays.stream(rawMappings.split(";"))
                .map(String::trim)
                .filter(entry -> !entry.isBlank())
                .forEach(entry -> {
                    String[] keyValue = entry.split("=", 2);
                    if (keyValue.length != 2) {
                        return;
                    }
                    String token = keyValue[0].trim();
                    if (token.isBlank()) {
                        return;
                    }
                    Set<String> values = Arrays.stream(keyValue[1].split("\\|"))
                            .map(String::trim)
                            .filter(value -> !value.isBlank())
                            .collect(LinkedHashSet::new, Set::add, Set::addAll);
                    mappedValues.put(token, values);
                });
        return mappedValues;
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

    private record AuthContext(String mode, Set<String> scopes, Set<String> allowedTenants) {
    }

    private record ScopeRule(String method, String path, boolean prefixMatch, Set<String> requiredScopes) {
        private boolean matches(String requestMethod, String requestPath) {
            if (!method.equals(requestMethod)) {
                return false;
            }
            return prefixMatch ? requestPath.startsWith(path) : path.equals(requestPath);
        }
    }
}
