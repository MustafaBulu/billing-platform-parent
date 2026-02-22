package com.mustafabulu.billing.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestCorrelationFilterTests {

    private final RequestCorrelationFilter filter = new RequestCorrelationFilter();

    @Test
    void shouldUseProvidedRequestId() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestCorrelationFilter.REQUEST_ID_HEADER, "req-1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        FilterChain chain = (req, res) -> {
            assertThat(req.getAttribute(RequestCorrelationFilter.REQUEST_ID_ATTR)).isEqualTo("req-1");
            assertThat(MDC.get(RequestCorrelationFilter.REQUEST_ID_ATTR)).isEqualTo("req-1");
        };

        filter.doFilter(request, response, chain);

        assertThat(response.getHeader(RequestCorrelationFilter.REQUEST_ID_HEADER)).isEqualTo("req-1");
        assertThat(MDC.get(RequestCorrelationFilter.REQUEST_ID_ATTR)).isNull();
    }

    @Test
    void shouldGenerateRequestIdWhenMissing() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader(RequestCorrelationFilter.REQUEST_ID_HEADER)).isNotBlank();
    }
}
