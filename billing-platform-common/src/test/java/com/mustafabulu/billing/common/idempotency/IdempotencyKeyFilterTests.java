package com.mustafabulu.billing.common.idempotency;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.ServletException;
import java.io.IOException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class IdempotencyKeyFilterTests {

    private final IdempotencyKeyFilter filter = new IdempotencyKeyFilter();

    @AfterEach
    void clearContext() {
        IdempotencyContextHolder.clear();
    }

    @Test
    void shouldSetContextInChainAndClearAfterwards() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(IdempotencyHeaders.IDEMPOTENCY_KEY, "idem-1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        MockFilterChain chain = new MockFilterChain((req, res) ->
                assertThat(IdempotencyContextHolder.getIdempotencyKey()).contains("idem-1"));

        filter.doFilter(request, response, chain);

        assertThat(IdempotencyContextHolder.getIdempotencyKey()).isEmpty();
    }

    @Test
    void shouldIgnoreBlankHeader() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(IdempotencyHeaders.IDEMPOTENCY_KEY, " ");
        MockHttpServletResponse response = new MockHttpServletResponse();

        MockFilterChain chain = new MockFilterChain((req, res) ->
                assertThat(IdempotencyContextHolder.getIdempotencyKey()).isEmpty());

        filter.doFilter(request, response, chain);

        assertThat(IdempotencyContextHolder.getIdempotencyKey()).isEmpty();
    }
}
