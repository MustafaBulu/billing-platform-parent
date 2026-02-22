package com.mustafabulu.billing.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import jakarta.servlet.ServletException;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

class ApiRateLimitFilterTests {

    private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder()
            .findAndAddModules()
            .build();

    private ApiRateLimitFilter newFilter(int maxRequests, long windowSeconds) {
        ApiRateLimitFilter filter = new ApiRateLimitFilter(OBJECT_MAPPER);
        ReflectionTestUtils.setField(filter, "enabled", true);
        ReflectionTestUtils.setField(filter, "maxRequests", maxRequests);
        ReflectionTestUtils.setField(filter, "windowSeconds", windowSeconds);
        return filter;
    }

    @Test
    void shouldAllowWhenUnderRateLimit() throws ServletException, IOException {
        ApiRateLimitFilter filter = newFilter(2, 60);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/payments/process");
        request.setRemoteAddr("127.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isNotEqualTo(429);
    }

    @Test
    void shouldReturnTooManyRequestsWhenLimitExceeded() throws ServletException, IOException {
        ApiRateLimitFilter filter = newFilter(1, 60);
        MockHttpServletRequest first = new MockHttpServletRequest("GET", "/api/v1/payments/process");
        first.setRemoteAddr("127.0.0.1");
        MockHttpServletResponse firstResponse = new MockHttpServletResponse();
        filter.doFilter(first, firstResponse, new MockFilterChain());

        MockHttpServletRequest second = new MockHttpServletRequest("GET", "/api/v1/payments/process");
        second.setRemoteAddr("127.0.0.1");
        MockHttpServletResponse secondResponse = new MockHttpServletResponse();
        filter.doFilter(second, secondResponse, new MockFilterChain());

        assertThat(secondResponse.getStatus()).isEqualTo(429);
        assertThat(secondResponse.getContentAsString()).contains("RATE_LIMIT_EXCEEDED");
    }

    @Test
    void shouldSkipPublicPaths() throws ServletException, IOException {
        ApiRateLimitFilter filter = newFilter(1, 60);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/system/health");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isNotEqualTo(429);
    }
}
