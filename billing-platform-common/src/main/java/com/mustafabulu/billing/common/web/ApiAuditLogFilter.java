package com.mustafabulu.billing.common.web;

import com.mustafabulu.billing.common.tenant.TenantContextFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 100)
public class ApiAuditLogFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(ApiAuditLogFilter.class);

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        long start = System.currentTimeMillis();
        try {
            filterChain.doFilter(request, response);
        } finally {
            if (log.isInfoEnabled()) {
                long latencyMs = System.currentTimeMillis() - start;
                log.info("audit method={} path={} status={} tenant={} requestId={} latencyMs={} remoteIp={}",
                        request.getMethod(),
                        request.getRequestURI(),
                        response.getStatus(),
                        safe(request.getHeader(TenantContextFilter.TENANT_HEADER)),
                        safe(resolveRequestId(request)),
                        latencyMs,
                        safe(request.getRemoteAddr()));
            }
        }
    }

    private String resolveRequestId(HttpServletRequest request) {
        Object requestId = request.getAttribute(RequestCorrelationFilter.REQUEST_ID_ATTR);
        if (requestId != null) {
            return requestId.toString();
        }
        return request.getHeader(RequestCorrelationFilter.REQUEST_ID_HEADER);
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }
}
