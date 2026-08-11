package com.nh.customermanager.config;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DemoWriteRequestFilterTests {

    private final DemoWriteRequestFilter filter =
            new DemoWriteRequestFilter();

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/demo",
            "/api/demo/",
            "/api/demo/customers",
            "/api/demo/customers/1",
            "/api/%64emo/customers",
            "/api/demo;v=1/customers"
    })
    void demoWritePathsAreForbidden(String path) throws Exception {
        assertForbidden("POST", path);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "POST",
            "PUT",
            "PATCH",
            "DELETE"
    })
    void demoWriteMethodsAreForbidden(String method) throws Exception {
        assertForbidden(method, "/api/demo/customers");
    }

    @Test
    void similarNonDemoPathContinuesFilterChain() throws Exception {
        assertContinuesFilterChain("POST", "/api/demoevil");
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "OPTIONS"})
    void readAndPreflightMethodsContinueFilterChain(String method)
            throws Exception {
        assertContinuesFilterChain(method, "/api/demo/customers");
    }

    private void assertForbidden(String method, String path)
            throws Exception {
        MockHttpServletRequest request = request(method, path);
        MockHttpServletResponse response =
                new MockHttpServletResponse();
        AtomicBoolean filterChainCalled = new AtomicBoolean();
        FilterChain filterChain = (servletRequest, servletResponse) ->
                filterChainCalled.set(true);

        assertNull(request.getRequestedSessionId());
        assertNull(request.getCookies());
        assertNull(request.getSession(false));

        filter.doFilter(request, response, filterChain);

        assertAll(
                () -> assertEquals(403, response.getStatus()),
                () -> assertFalse(filterChainCalled.get()),
                () -> assertNull(response.getHeader(
                        HttpHeaders.SET_COOKIE
                )),
                () -> assertNull(response.getCookie("JSESSIONID")),
                () -> assertNull(request.getSession(false))
        );
    }

    private void assertContinuesFilterChain(String method, String path)
            throws Exception {
        MockHttpServletRequest request = request(method, path);
        MockHttpServletResponse response =
                new MockHttpServletResponse();
        AtomicBoolean filterChainCalled = new AtomicBoolean();
        FilterChain filterChain = (servletRequest, servletResponse) ->
                filterChainCalled.set(true);

        filter.doFilter(request, response, filterChain);

        assertTrue(filterChainCalled.get());
    }

    private MockHttpServletRequest request(String method, String path) {
        MockHttpServletRequest request =
                new MockHttpServletRequest();
        request.setMethod(method);
        request.setRequestURI(path);
        return request;
    }
}
