package com.mustafabulu.billing.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

class ApiSecurityFilterTests {

    private ApiSecurityFilter newFilter(String mode,
                                        boolean authzEnabled,
                                        boolean tenantGuardEnabled,
                                        String apiKey,
                                        String bearerTokens,
                                        String tokenScopes,
                                        String tokenTenants) {
        ApiSecurityFilter filter = new ApiSecurityFilter(new ObjectMapper());
        ReflectionTestUtils.setField(filter, "authMode", mode);
        ReflectionTestUtils.setField(filter, "authorizationEnabled", authzEnabled);
        ReflectionTestUtils.setField(filter, "tenantGuardEnabled", tenantGuardEnabled);
        ReflectionTestUtils.setField(filter, "expectedApiKey", apiKey);
        ReflectionTestUtils.setField(filter, "bearerTokens", bearerTokens);
        ReflectionTestUtils.setField(filter, "bearerTokenScopes", tokenScopes);
        ReflectionTestUtils.setField(filter, "bearerTokenTenants", tokenTenants);
        return filter;
    }

    @Test
    void shouldAllowPublicPathWithoutAuth() throws ServletException, IOException {
        ApiSecurityFilter filter = newFilter("bearer", true, true, "", "", "", "");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/system/health");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isNotEqualTo(401);
    }

    @Test
    void shouldRejectInvalidApiKey() throws ServletException, IOException {
        ApiSecurityFilter filter = newFilter("api-key", false, false, "secret", "", "", "");
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/tenants");
        request.addHeader("X-API-Key", "wrong");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("AUTH_INVALID_API_KEY");
    }

    @Test
    void shouldRejectMissingBearerToken() throws ServletException, IOException {
        ApiSecurityFilter filter = newFilter("bearer", false, false, "", "token-a", "", "");
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/tenants");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("AUTH_BEARER_REQUIRED");
    }

    @Test
    void shouldForbidMissingScopeWhenAuthorizationEnabled() throws ServletException, IOException {
        ApiSecurityFilter filter = newFilter(
                "bearer",
                true,
                false,
                "",
                "token-a",
                "token-a=usage:read",
                "token-a=*");

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/tenants");
        request.addHeader("Authorization", "Bearer token-a");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("AUTHZ_SCOPE_FORBIDDEN");
    }

    @Test
    void shouldAllowBearerWithRequiredScopeAndTenant() throws ServletException, IOException {
        ApiSecurityFilter filter = newFilter(
                "bearer",
                true,
                true,
                "",
                "token-a",
                "token-a=tenant:write",
                "token-a=tenant-1");

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/tenants");
        request.addHeader("Authorization", "Bearer token-a");
        request.addHeader("X-Tenant-Id", "tenant-1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isNotEqualTo(401);
        assertThat(response.getStatus()).isNotEqualTo(403);
    }
}
