package com.nh.customermanager;

import com.nh.customermanager.config.SecurityConfig;

import java.sql.Connection;
import java.util.List;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.DefaultCorsProcessor;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:cors_configuration_test;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;IGNORECASE=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=cors_test_only_user",
        "spring.datasource.password=cors_test_only_database_password",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false",
        "ADMIN_USERNAME=cors-test-only-admin",
        "ADMIN_PASSWORD=cors-test-only-admin-password-2026",
        "app.cors.allowed-origin=https://portfolio.example"
})
@AutoConfigureMockMvc
class CorsConfigurationIntegrationTests {

    private static final String CONFIGURED_ORIGIN = "https://portfolio.example";
    private static final String INVALID_ORIGIN_MESSAGE =
            "Invalid app.cors.allowed-origin: expected one exact HTTP(S) origin";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DataSource dataSource;

    @Test
    void usesIsolatedH2Database() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            String databaseUrl = connection.getMetaData().getURL();

            assertTrue(databaseUrl.startsWith("jdbc:h2:mem:cors_configuration_test"));
            assertFalse(databaseUrl.startsWith("jdbc:mysql:"));
        }
    }

    @Test
    void configuredOriginAllowsPingGetPreflight() throws Exception {
        assertAllowedPreflight("/api/ping", HttpMethod.GET);
    }

    @Test
    void configuredOriginAllowsCustomersGetPreflight() throws Exception {
        assertAllowedPreflight("/api/customers", HttpMethod.GET);
    }

    @Test
    void configuredOriginAllowsDemoCustomersHeadPreflight() throws Exception {
        assertAllowedPreflight("/api/demo/customers", HttpMethod.HEAD)
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS,
                        containsString(HttpMethod.HEAD.name())
                ));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://localhost:5173",
            "http://127.0.0.1:5173"
    })
    void configuredOriginRejectsUnconfiguredLocalOrigins(String origin) throws Exception {
        assertRejectedPreflight("/api/ping", HttpMethod.GET, origin);
        assertRejectedPreflight("/api/customers", HttpMethod.GET, origin);
        assertRejectedPreflight("/api/demo/customers", HttpMethod.HEAD, origin);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            " ",
            "https://a.example,https://b.example",
            "https://a.example, https://b.example",
            "*",
            "https://*.example",
            "https://portfolio.example/path",
            "https://portfolio.example?x=1",
            "https://portfolio.example#section",
            "ftp://portfolio.example",
            "https:///missing-host",
            "https://user@portfolio.example",
            "https://portfolio.example:0",
            "https://portfolio.example:65536",
            "https://portfolio.example:not-a-port"
    })
    void invalidConfiguredOriginsFailBeforeRegistration(String configuredOrigin) {
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> new SecurityConfig().corsConfigurationSource(configuredOrigin)
        );

        assertEquals(INVALID_ORIGIN_MESSAGE, exception.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://localhost:5173",
            "https://portfolio.example",
            "https://portfolio.example:8443"
    })
    void exactHttpOriginsRegisterAsSingleAllowedOrigin(String configuredOrigin) {
        assertRegisteredOrigin(configuredOrigin, configuredOrigin);
    }

    @Test
    void configuredOriginIsTrimmedBeforeRegistration() {
        assertRegisteredOrigin(
                "  https://portfolio.example:8443  ",
                "https://portfolio.example:8443"
        );
    }

    @ParameterizedTest
    @CsvSource({
            "https://portfolio.example:443, https://portfolio.example",
            "http://portfolio.example:80, http://portfolio.example"
    })
    void defaultPortsAreRemovedAndBrowserPreflightsSucceed(
            String configuredOrigin,
            String browserOrigin
    ) throws Exception {
        assertRegisteredOriginSupportsPreflight(
                configuredOrigin,
                browserOrigin
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://portfolio.example:8443",
            "http://portfolio.example:8080"
    })
    void nonDefaultPortsArePreserved(String configuredOrigin)
            throws Exception {
        assertRegisteredOriginSupportsPreflight(
                configuredOrigin,
                configuredOrigin
        );
    }

    @Test
    void schemeAndHostAreCanonicalized() throws Exception {
        assertRegisteredOriginSupportsPreflight(
                "HTTPS://PORTFOLIO.EXAMPLE:8443",
                "https://portfolio.example:8443"
        );
    }

    private ResultActions assertAllowedPreflight(String path, HttpMethod requestedMethod) throws Exception {
        return preflight(path, requestedMethod, CONFIGURED_ORIGIN)
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, CONFIGURED_ORIGIN))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"))
                .andExpect(result -> assertFalse(
                        "*".equals(result.getResponse().getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN))
                ));
    }

    private void assertRejectedPreflight(String path, HttpMethod requestedMethod, String origin) throws Exception {
        preflight(path, requestedMethod, origin)
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    private ResultActions preflight(String path, HttpMethod requestedMethod, String origin) throws Exception {
        return mockMvc.perform(options(path)
                .header(HttpHeaders.ORIGIN, origin)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, requestedMethod.name()));
    }

    private void assertRegisteredOrigin(String configuredOrigin, String expectedOrigin) {
        CorsConfigurationSource source =
                new SecurityConfig().corsConfigurationSource(configuredOrigin);
        CorsConfiguration configuration = source.getCorsConfiguration(
                new MockHttpServletRequest("GET", "/api/ping")
        );

        assertNotNull(configuration);
        assertEquals(List.of(expectedOrigin), configuration.getAllowedOrigins());
    }

    private void assertRegisteredOriginSupportsPreflight(
            String configuredOrigin,
            String browserOrigin
    ) throws Exception {
        CorsConfigurationSource source =
                new SecurityConfig().corsConfigurationSource(configuredOrigin);
        CorsConfiguration configuration = source.getCorsConfiguration(
                new MockHttpServletRequest("GET", "/api/ping")
        );
        assertNotNull(configuration);

        MockHttpServletRequest request =
                new MockHttpServletRequest("OPTIONS", "/api/ping");
        request.addHeader(HttpHeaders.ORIGIN, browserOrigin);
        request.addHeader(
                HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD,
                HttpMethod.GET.name()
        );
        MockHttpServletResponse response =
                new MockHttpServletResponse();

        boolean accepted = new DefaultCorsProcessor().processRequest(
                configuration,
                request,
                response
        );

        assertAll(
                () -> assertEquals(
                        List.of(browserOrigin),
                        configuration.getAllowedOrigins()
                ),
                () -> assertTrue(accepted),
                () -> assertEquals(
                        HttpStatus.OK.value(),
                        response.getStatus()
                ),
                () -> assertEquals(
                        browserOrigin,
                        response.getHeader(
                                HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN
                        )
                )
        );
    }
}
