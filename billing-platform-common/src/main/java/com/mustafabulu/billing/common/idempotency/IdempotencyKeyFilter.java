package com.mustafabulu.billing.common.idempotency;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

import org.springframework.lang.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;

public class IdempotencyKeyFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        String idempotencyKey = request.getHeader(IdempotencyHeaders.IDEMPOTENCY_KEY);
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            IdempotencyContextHolder.setIdempotencyKey(idempotencyKey);
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            IdempotencyContextHolder.clear();
        }
    }
}
