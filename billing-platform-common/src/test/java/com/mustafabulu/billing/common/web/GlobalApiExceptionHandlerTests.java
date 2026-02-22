package com.mustafabulu.billing.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.mustafabulu.billing.common.exception.ConflictException;
import com.mustafabulu.billing.common.exception.DomainValidationException;
import com.mustafabulu.billing.common.exception.ResourceNotFoundException;
import com.mustafabulu.billing.common.exception.UpstreamServiceException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

class GlobalApiExceptionHandlerTests {

    private final GlobalApiExceptionHandler handler = new GlobalApiExceptionHandler();

    private MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/test");
        request.addHeader(RequestCorrelationFilter.REQUEST_ID_HEADER, "req-1");
        return request;
    }

    @Test
    void shouldMapDomainValidationToBadRequest() {
        var response = handler.handleDomainValidation(new DomainValidationException("invalid"), request());

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("DOMAIN_VALIDATION_ERROR");
    }

    @Test
    void shouldMapNotFound() {
        var response = handler.handleNotFound(new ResourceNotFoundException("missing"), request());

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("NOT_FOUND");
    }

    @Test
    void shouldMapConflict() {
        var response = handler.handleConflict(new ConflictException("conflict"), request());

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("CONFLICT");
    }

    @Test
    void shouldMapUpstreamDependencyError() {
        var response = handler.handleUpstreamService(new UpstreamServiceException("upstream"), request());

        assertThat(response.getStatusCode().value()).isEqualTo(424);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("FAILED_DEPENDENCY");
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldMapUnreadableBody() throws Exception {
        Class<?> unreadableType = Class.forName("org.springframework.http.converter.HttpMessageNotReadableException");
        Object unreadable = unreadableType.getConstructor(String.class, HttpInputMessage.class)
                .newInstance("bad", emptyInputMessage());
        Method method = GlobalApiExceptionHandler.class.getMethod(
                "handleUnreadableBody",
                unreadableType,
                jakarta.servlet.http.HttpServletRequest.class);
        ResponseEntity<ApiErrorResponse> response =
                (ResponseEntity<ApiErrorResponse>) method.invoke(handler, unreadable, request());

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("INVALID_JSON");
    }

    @Test
    void shouldMapConstraintViolation() {
        @SuppressWarnings("unchecked")
        ConstraintViolation<Object> violation = Mockito.mock(ConstraintViolation.class);
        Path path = Mockito.mock(Path.class);
        Mockito.when(path.toString()).thenReturn("tenantId");
        Mockito.when(violation.getPropertyPath()).thenReturn(path);
        Mockito.when(violation.getMessage()).thenReturn("must not be blank");

        var response = handler.handleConstraintViolation(new ConstraintViolationException(Set.of(violation)), request());

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("CONSTRAINT_VIOLATION");
        assertThat(response.getBody().details()).containsEntry("tenantId", "must not be blank");
    }

    @Test
    void shouldMapResponseStatusException() {
        var response = handler.handleResponseStatus(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "auth"), request());

        assertThat(response.getStatusCode().value()).isEqualTo(401);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("HTTP_401");
    }

    @Test
    void shouldMapUnexpectedException() {
        var response = handler.handleUnexpected(new RuntimeException("boom"), request());

        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("INTERNAL_ERROR");
    }

    private static HttpInputMessage emptyInputMessage() {
        return new HttpInputMessage() {
            @Override
            public InputStream getBody() {
                return new ByteArrayInputStream(new byte[0]);
            }

            @Override
            public HttpHeaders getHeaders() {
                return HttpHeaders.EMPTY;
            }
        };
    }
}
