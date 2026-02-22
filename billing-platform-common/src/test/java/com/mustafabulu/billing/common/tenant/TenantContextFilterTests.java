package com.mustafabulu.billing.common.tenant;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class TenantContextFilterTests {

    private final TenantContextFilter filter = new TenantContextFilter();

    @Test
    void shouldSetTenantInFilterChainAndClearAfterwards() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(TenantContextFilter.TENANT_HEADER, "tenant-a");
        MockHttpServletResponse response = new MockHttpServletResponse();

        FilterChain chain = (req, res) ->
                assertThat(TenantContextHolder.getTenantId()).contains("tenant-a");

        filter.doFilter(request, response, chain);

        assertThat(TenantContextHolder.getTenantId()).isEmpty();
    }

    @Test
    void shouldNotSetTenantForBlankHeader() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(TenantContextFilter.TENANT_HEADER, "   ");
        MockHttpServletResponse response = new MockHttpServletResponse();

        FilterChain chain = (req, res) ->
                assertThat(TenantContextHolder.getTenantId()).isEmpty();

        filter.doFilter(request, response, chain);

        assertThat(TenantContextHolder.getTenantId()).isEmpty();
    }
}
