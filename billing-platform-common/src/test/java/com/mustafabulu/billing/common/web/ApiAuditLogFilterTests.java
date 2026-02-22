package com.mustafabulu.billing.common.web;

import static org.assertj.core.api.Assertions.assertThatNoException;

import com.mustafabulu.billing.common.tenant.TenantContextFilter;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class ApiAuditLogFilterTests {

    private final ApiAuditLogFilter filter = new ApiAuditLogFilter();

    @Test
    void shouldLogRequestWithRequestIdFromAttribute() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/test");
        request.setAttribute(RequestCorrelationFilter.REQUEST_ID_ATTR, "req-attr");
        request.addHeader(TenantContextFilter.TENANT_HEADER, "tenant-1");
        request.setRemoteAddr("127.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> response.setStatus(202);

        assertThatNoException().isThrownBy(() -> filter.doFilter(request, response, chain));
    }

    @Test
    void shouldLogRequestWithRequestIdFromHeaderWhenAttributeMissing() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/test");
        request.addHeader(RequestCorrelationFilter.REQUEST_ID_HEADER, "req-header");
        request.setRemoteAddr("127.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> response.setStatus(201);

        assertThatNoException().isThrownBy(() -> filter.doFilter(request, response, chain));
    }
}
