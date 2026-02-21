package com.mustafabulu.billing.common.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mustafabulu.billing.common.tenant.TenantContextFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class ApiRateLimitFilter extends OncePerRequestFilter {
    private static final String RATE_LIMIT_CODE = "RATE_LIMIT_EXCEEDED";
    private static final String RATE_LIMIT_MESSAGE = "Too many requests for current rate limit window";

    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<String, WindowCounter> counters = new ConcurrentHashMap<>();

    @Value("${platform.rate-limit.enabled:false}")
    private boolean enabled;

    @Value("${platform.rate-limit.max-requests:120}")
    private int maxRequests;

    @Value("${platform.rate-limit.window-seconds:60}")
    private long windowSeconds;

    public ApiRateLimitFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        if (!enabled || isPublicPath(request.getRequestURI())) {
            filterChain.doFilter(request, response);
            return;
        }

        long now = System.currentTimeMillis();
        long windowMs = windowSeconds * 1000L;
        String key = resolveLimitKey(request);
        WindowCounter counter = counters.compute(key, (k, current) -> {
            if (current == null || now - current.windowStartMs > windowMs) {
                return new WindowCounter(now, new AtomicInteger(1));
            }
            current.counter.incrementAndGet();
            return current;
        });

        if (counter.counter.get() > maxRequests) {
            writeRateLimitError(response, request);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String resolveLimitKey(HttpServletRequest request) {
        String tenant = request.getHeader(TenantContextFilter.TENANT_HEADER);
        if (tenant != null && !tenant.isBlank()) {
            return "tenant:" + tenant.trim();
        }
        String ip = request.getRemoteAddr();
        return "ip:" + (ip == null ? "unknown" : ip);
    }

    private boolean isPublicPath(String path) {
        return path.startsWith("/actuator")
                || path.startsWith("/swagger-ui")
                || path.startsWith("/v3/api-docs")
                || path.startsWith("/api/v1/system");
    }

    private void writeRateLimitError(@NonNull HttpServletResponse response,
                                     @NonNull HttpServletRequest request) throws IOException {
        HttpStatus status = HttpStatus.TOO_MANY_REQUESTS;
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ApiErrorResponse body = new ApiErrorResponse(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                RATE_LIMIT_CODE,
                RATE_LIMIT_MESSAGE,
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

    private record WindowCounter(long windowStartMs, AtomicInteger counter) {
    }
}
