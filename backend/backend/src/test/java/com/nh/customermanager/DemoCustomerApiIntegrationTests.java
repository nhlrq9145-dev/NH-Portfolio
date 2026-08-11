package com.nh.customermanager;

import com.jayway.jsonpath.JsonPath;
import com.nh.customermanager.repository.CustomerRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import javax.sql.DataSource;
import java.lang.reflect.RecordComponent;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:demo_customer_api_test;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;IGNORECASE=TRUE",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=demo_test_only_user",
                "spring.datasource.password=demo_test_only_database_password",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "spring.jpa.show-sql=false",
                "ADMIN_USERNAME=auth-test-only-admin",
                "ADMIN_PASSWORD=auth-test-only-admin-password-2026"
        }
)
@AutoConfigureMockMvc
class DemoCustomerApiIntegrationTests {

    private static final String DEMO_CUSTOMER_PATH =
            "/api/demo/customers";

    private static final Set<String> PUBLIC_FIELDS = Set.of(
            "displayName",
            "industry",
            "status"
    );

    private static final Set<String> FORBIDDEN_FIELDS = Set.of(
            "id",
            "phone",
            "email",
            "createdAt"
    );

    private static final String LOGIN_JSON = """
            {
              "username": "auth-test-only-admin",
              "password": "auth-test-only-admin-password-2026"
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DataSource dataSource;

    @LocalServerPort
    private int serverPort;

    @MockitoBean(enforceOverride = true)
    private CustomerRepository customerRepository;

    @Test
    void demoCustomerResponseHasExactPublicContract() {
        Class<?> responseType = assertDoesNotThrow(
                () -> Class.forName(
                        "com.nh.customermanager.dto.DemoCustomerResponse"
                )
        );

        assertTrue(responseType.isRecord());
        assertEquals(
                List.of("displayName", "industry", "status"),
                Arrays.stream(responseType.getRecordComponents())
                        .map(RecordComponent::getName)
                        .toList()
        );
    }

    @Test
    void unauthenticatedDemoRequestReturnsOnlyFixedSafeFields()
            throws Exception {
        MvcResult result = mockMvc.perform(get(DEMO_CUSTOMER_PATH))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(jsonPath("$").isArray())
                .andReturn();

        assertNull(result.getRequest().getSession(false));
        assertNull(result.getResponse().getCookie("JSESSIONID"));
        assertNull(result.getResponse().getHeader(
                HttpHeaders.SET_COOKIE
        ));

        List<Map<String, Object>> demoCustomers = JsonPath.read(
                result.getResponse().getContentAsString(
                        StandardCharsets.UTF_8
                ),
                "$"
        );

        assertFalse(demoCustomers.isEmpty());
        for (Map<String, Object> demoCustomer : demoCustomers) {
            assertEquals(PUBLIC_FIELDS, demoCustomer.keySet());
            for (String forbiddenField : FORBIDDEN_FIELDS) {
                assertFalse(demoCustomer.containsKey(forbiddenField));
            }
            assertTrue(
                    String.valueOf(demoCustomer.get("displayName"))
                            .startsWith("演示客户")
            );
        }
    }

    @Test
    void demoRequestDoesNotUseCustomerRepository() throws Exception {
        assertUsesIsolatedH2Database();

        mockMvc.perform(get(DEMO_CUSTOMER_PATH))
                .andExpect(status().isOk());

        verifyNoInteractions(customerRepository);
    }

    @Test
    void unauthenticatedHeadDemoRequestIsPublicAndStateless()
            throws Exception {
        HttpResponse<byte[]> httpResult = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(
                                URI.create(
                                        "http://127.0.0.1:"
                                                + serverPort
                                                + DEMO_CUSTOMER_PATH
                                )
                        )
                        .method(
                                HttpMethod.HEAD.name(),
                                HttpRequest.BodyPublishers.noBody()
                        )
                        .build(),
                HttpResponse.BodyHandlers.ofByteArray()
        );

        MvcResult mockResult = mockMvc.perform(
                        request(
                                HttpMethod.HEAD,
                                URI.create(DEMO_CUSTOMER_PATH)
                        )
                )
                .andReturn();

        assertAll(
                () -> assertEquals(
                        200,
                        httpResult.statusCode()
                ),
                () -> assertEquals(
                        0,
                        httpResult.body().length
                ),
                () -> assertTrue(
                        httpResult.headers()
                                .allValues(HttpHeaders.SET_COOKIE)
                                .isEmpty()
                ),
                () -> assertTrue(
                        httpResult.headers().map().values().stream()
                                .flatMap(List::stream)
                                .noneMatch(value ->
                                        value.contains("JSESSIONID")
                                )
                ),
                () -> assertEquals(
                        200,
                        mockResult.getResponse().getStatus()
                ),
                () -> assertNull(
                        mockResult.getRequest().getRequestedSessionId()
                ),
                () -> assertNull(mockResult.getRequest().getCookies()),
                () -> assertNull(
                        mockResult.getRequest().getSession(false)
                ),
                () -> assertNull(mockResult.getResponse().getHeader(
                        HttpHeaders.SET_COOKIE
                )),
                () -> assertNull(mockResult.getResponse().getCookie(
                        "JSESSIONID"
                )),
                () -> verifyNoInteractions(customerRepository)
        );
    }

    @Test
    void corsPreflightAllowsHeadOnlyForConfiguredOrigin()
            throws Exception {
        String allowedOrigin = "http://localhost:5173";

        MvcResult allowedResult = mockMvc.perform(
                        request(
                                HttpMethod.OPTIONS,
                                URI.create(DEMO_CUSTOMER_PATH)
                        )
                                .header(
                                        HttpHeaders.ORIGIN,
                                        allowedOrigin
                                )
                                .header(
                                        HttpHeaders
                                                .ACCESS_CONTROL_REQUEST_METHOD,
                                        HttpMethod.HEAD.name()
                                )
                )
                .andReturn();

        MvcResult disallowedResult = mockMvc.perform(
                        request(
                                HttpMethod.OPTIONS,
                                URI.create(DEMO_CUSTOMER_PATH)
                        )
                                .header(
                                        HttpHeaders.ORIGIN,
                                        "http://127.0.0.1:5173"
                                )
                                .header(
                                        HttpHeaders
                                                .ACCESS_CONTROL_REQUEST_METHOD,
                                        HttpMethod.HEAD.name()
                                )
                )
                .andReturn();

        String allowedMethods = allowedResult.getResponse().getHeader(
                HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS
        );

        assertAll(
                () -> assertEquals(
                        200,
                        allowedResult.getResponse().getStatus()
                ),
                () -> assertEquals(
                        allowedOrigin,
                        allowedResult.getResponse().getHeader(
                                HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN
                        )
                ),
                () -> assertNotNull(allowedMethods),
                () -> assertTrue(
                        Arrays.stream(allowedMethods.split(","))
                                .map(String::trim)
                                .anyMatch(HttpMethod.HEAD.name()::equals)
                ),
                () -> assertEquals(
                        403,
                        disallowedResult.getResponse().getStatus()
                ),
                () -> assertNull(
                        disallowedResult.getResponse().getHeader(
                                HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN
                        )
                )
        );
    }

    @Test
    void unauthenticatedHeadCustomerRequestRemainsUnauthorized()
            throws Exception {
        mockMvc.perform(
                        request(
                                HttpMethod.HEAD,
                                URI.create("/api/customers")
                        )
                )
                .andExpect(status().isUnauthorized());
    }

    @Test
    void demoNamespaceRejectsWriteMethods() throws Exception {
        MockHttpSession session = loginAndGetSession();

        assertAll(
                () -> expectForbidden(
                        post(DEMO_CUSTOMER_PATH).session(session)
                ),
                () -> expectForbidden(
                        put(DEMO_CUSTOMER_PATH).session(session)
                ),
                () -> expectForbidden(
                        delete(DEMO_CUSTOMER_PATH).session(session)
                ),
                () -> verifyNoInteractions(customerRepository)
        );
    }

    @ParameterizedTest
    @MethodSource("anonymousDemoWriteRequests")
    void anonymousDemoWriteRequestsAreForbiddenWithoutSessionOrCookie(
            HttpMethod method,
            String path
    ) throws Exception {
        MvcResult result = mockMvc.perform(
                        request(method, URI.create(path))
                )
                .andReturn();

        assertAll(
                () -> assertNull(
                        result.getRequest().getRequestedSessionId()
                ),
                () -> assertNull(result.getRequest().getCookies()),
                () -> assertEquals(
                        403,
                        result.getResponse().getStatus()
                ),
                () -> assertNull(result.getResponse().getHeader(
                        HttpHeaders.SET_COOKIE
                )),
                () -> assertNull(result.getResponse().getCookie(
                        "JSESSIONID"
                )),
                () -> assertNull(
                        result.getRequest().getSession(false)
                ),
                () -> verifyNoInteractions(customerRepository)
        );
    }

    private static Stream<Arguments> anonymousDemoWriteRequests() {
        return Stream.of(
                Arguments.of(
                        HttpMethod.POST,
                        DEMO_CUSTOMER_PATH
                ),
                Arguments.of(
                        HttpMethod.PUT,
                        DEMO_CUSTOMER_PATH + "/1"
                ),
                Arguments.of(
                        HttpMethod.DELETE,
                        DEMO_CUSTOMER_PATH + "/1"
                ),
                Arguments.of(
                        HttpMethod.POST,
                        "/api/%64emo/customers"
                ),
                Arguments.of(
                        HttpMethod.PUT,
                        "/api/%64emo/customers"
                ),
                Arguments.of(
                        HttpMethod.PATCH,
                        "/api/%64emo/customers"
                ),
                Arguments.of(
                        HttpMethod.DELETE,
                        "/api/%64emo/customers"
                ),
                Arguments.of(
                        HttpMethod.POST,
                        "/api/demo;v=1/customers"
                ),
                Arguments.of(
                        HttpMethod.PUT,
                        "/api/demo;v=1/customers"
                ),
                Arguments.of(
                        HttpMethod.PATCH,
                        "/api/demo;v=1/customers"
                ),
                Arguments.of(
                        HttpMethod.DELETE,
                        "/api/demo;v=1/customers"
                )
        );
    }

    private void assertUsesIsolatedH2Database() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            assertTrue(
                    connection.getMetaData()
                            .getURL()
                            .startsWith(
                                    "jdbc:h2:mem:demo_customer_api_test"
                            )
            );
        }
    }

    private MockHttpSession loginAndGetSession() throws Exception {
        MvcResult result = mockMvc.perform(
                        post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(LOGIN_JSON)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andReturn();

        assertTrue(
                result.getRequest().getSession(false)
                        instanceof MockHttpSession
        );

        MockHttpSession session = (MockHttpSession) result
                .getRequest()
                .getSession(false);
        assertFalse(session.isInvalid());
        assertNotNull(session.getAttribute(
                HttpSessionSecurityContextRepository
                        .SPRING_SECURITY_CONTEXT_KEY
        ));
        return session;
    }

    private void expectForbidden(
            MockHttpServletRequestBuilder request
    ) throws Exception {
        mockMvc.perform(request)
                .andExpect(status().isForbidden());
    }
}
