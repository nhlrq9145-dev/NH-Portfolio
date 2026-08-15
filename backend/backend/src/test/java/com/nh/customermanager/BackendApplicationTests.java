package com.nh.customermanager;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.util.Map;
import java.util.Set;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:auth_integration_test;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;IGNORECASE=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=auth_test_only_user",
        "spring.datasource.password=auth_test_only_database_password",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false",
        "ADMIN_USERNAME=auth-test-only-admin",
        "ADMIN_PASSWORD=auth-test-only-admin-password-2026"
})
@AutoConfigureMockMvc
class BackendApplicationTests {

    private static final Set<String> CSRF_RESPONSE_FIELDS = Set.of(
            "headerName",
            "token"
    );

    private static final String CORRECT_LOGIN_JSON = """
            {
              "username": "auth-test-only-admin",
              "password": "auth-test-only-admin-password-2026"
            }
            """;

    private static final String WRONG_PASSWORD_LOGIN_JSON = """
            {
              "username": "auth-test-only-admin",
              "password": "wrong-test-only-password"
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DataSource dataSource;

    @Test
    void usesIsolatedH2Database() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            assertTrue(
                    connection.getMetaData()
                            .getURL()
                            .startsWith(
                                    "jdbc:h2:mem:auth_integration_test"
                            )
            );
            assertFalse(
                    connection.getMetaData()
                            .getURL()
                            .startsWith("jdbc:mysql:")
            );
        }
    }

    @Test
    void csrfEndpointIsPublicAndReturnsStrictNoStoreContract()
            throws Exception {
        CsrfSession csrf = getCsrfSession();

        assertFalse(csrf.session().isInvalid());
        assertNotEquals(csrf.session().getId(), csrf.token());
    }

    @Test
    void unauthenticatedCustomerRequestReturnsUnauthorized()
            throws Exception {
        mockMvc.perform(get("/api/customers"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void loginWithoutCsrfTokenReturnsForbidden() throws Exception {
        expectCsrfForbidden(
                post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CORRECT_LOGIN_JSON)
        );
    }

    @Test
    void loginWithInvalidCsrfTokenReturnsForbidden()
            throws Exception {
        CsrfSession csrf = getCsrfSession();

        expectCsrfForbidden(
                post("/api/auth/login")
                        .session(csrf.session())
                        .header(csrf.headerName(), "invalid-test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CORRECT_LOGIN_JSON)
        );
    }

    @Test
    void loginWithAnotherSessionsCsrfTokenReturnsForbidden()
            throws Exception {
        CsrfSession first = getCsrfSession();
        CsrfSession second = getCsrfSession();

        expectCsrfForbidden(
                post("/api/auth/login")
                        .session(second.session())
                        .header(first.headerName(), first.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CORRECT_LOGIN_JSON)
        );
    }

    @Test
    void wrongPasswordWithValidCsrfTokenReturnsUnauthorized()
            throws Exception {
        CsrfSession csrf = getCsrfSession();

        mockMvc.perform(post("/api/auth/login")
                        .session(csrf.session())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(WRONG_PASSWORD_LOGIN_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value(
                        "用户名或密码错误"
                ));
    }

    @Test
    void correctCredentialsCreateAuthenticatedSession()
            throws Exception {
        AuthenticatedSession authenticated = loginAndGetSession();

        assertFalse(authenticated.session().isInvalid());
        assertNotNull(authenticated.session().getAttribute(
                HttpSessionSecurityContextRepository
                        .SPRING_SECURITY_CONTEXT_KEY
        ));
    }

    @Test
    void successfulLoginInvalidatesOldCsrfToken()
            throws Exception {
        AuthenticatedSession authenticated = loginAndGetSession();

        expectCsrfForbidden(
                post("/api/auth/logout")
                        .session(authenticated.session())
                        .header(
                                authenticated.headerName(),
                                authenticated.preLoginToken()
                        )
        );
        assertFalse(authenticated.session().isInvalid());
    }

    @Test
    void authenticatedSessionCanAccessCurrentUser()
            throws Exception {
        AuthenticatedSession authenticated = loginAndGetSession();

        mockMvc.perform(get("/api/auth/me")
                        .session(authenticated.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.username").value(
                        "auth-test-only-admin"
                ));
    }

    @Test
    void authenticatedSessionCanAccessCustomers()
            throws Exception {
        AuthenticatedSession authenticated = loginAndGetSession();

        mockMvc.perform(get("/api/customers")
                        .session(authenticated.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void logoutWithoutOrWithInvalidCsrfTokenIsForbidden()
            throws Exception {
        AuthenticatedSession authenticated = loginAndGetSession();

        expectCsrfForbidden(
                post("/api/auth/logout")
                        .session(authenticated.session())
        );
        assertFalse(authenticated.session().isInvalid());

        expectCsrfForbidden(
                post("/api/auth/logout")
                        .session(authenticated.session())
                        .header(
                                authenticated.headerName(),
                                "invalid-test-token"
                        )
        );
        assertFalse(authenticated.session().isInvalid());
    }

    @Test
    void logoutInvalidatesSessionAndAllowsNewAnonymousCsrfSession()
            throws Exception {
        AuthenticatedSession authenticated = loginAndGetSession();

        mockMvc.perform(post("/api/auth/logout")
                        .session(authenticated.session())
                        .header(
                                authenticated.headerName(),
                                authenticated.token()
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("退出成功"));

        assertTrue(authenticated.session().isInvalid());

        mockMvc.perform(get("/api/customers")
                        .session(authenticated.session()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        CsrfSession newAnonymousSession = getCsrfSession();
        assertNotEquals(authenticated.token(), newAnonymousSession.token());
        assertNotEquals(
                authenticated.session().getId(),
                newAnonymousSession.session().getId()
        );
    }

    private AuthenticatedSession loginAndGetSession()
            throws Exception {
        CsrfSession preLoginCsrf = getCsrfSession();
        MvcResult result = mockMvc.perform(
                        post("/api/auth/login")
                                .session(preLoginCsrf.session())
                                .header(
                                        preLoginCsrf.headerName(),
                                        preLoginCsrf.token()
                                )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(CORRECT_LOGIN_JSON)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.username").value(
                        "auth-test-only-admin"
                ))
                .andReturn();

        assertTrue(
                result.getRequest().getSession(false)
                        instanceof MockHttpSession
        );

        MockHttpSession session = (MockHttpSession) result
                .getRequest()
                .getSession(false);
        CsrfSession postLoginCsrf = getCsrfSession(session);
        assertNotEquals(preLoginCsrf.token(), postLoginCsrf.token());

        return new AuthenticatedSession(
                session,
                postLoginCsrf.headerName(),
                postLoginCsrf.token(),
                preLoginCsrf.token()
        );
    }

    private CsrfSession getCsrfSession() throws Exception {
        return getCsrfSession(null);
    }

    private CsrfSession getCsrfSession(
            MockHttpSession existingSession
    ) throws Exception {
        MockHttpServletRequestBuilder request = get("/api/auth/csrf");
        if (existingSession != null) {
            request.session(existingSession);
        }

        MvcResult result = mockMvc.perform(request)
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        containsString("no-store")
                ))
                .andExpect(jsonPath("$.headerName").isNotEmpty())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString(
                StandardCharsets.UTF_8
        );
        Map<String, Object> response = JsonPath.read(responseBody, "$" );
        assertEquals(CSRF_RESPONSE_FIELDS, response.keySet());
        assertTrue(
                result.getRequest().getSession(false)
                        instanceof MockHttpSession
        );

        return new CsrfSession(
                (MockHttpSession) result.getRequest().getSession(false),
                String.valueOf(response.get("headerName")),
                String.valueOf(response.get("token"))
        );
    }

    private ResultActions expectCsrfForbidden(
            MockHttpServletRequestBuilder request
    ) throws Exception {
        return mockMvc.perform(request)
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value(
                        "请求安全校验失败，请刷新页面后重试"
                ));
    }

    private record CsrfSession(
            MockHttpSession session,
            String headerName,
            String token
    ) {
    }

    private record AuthenticatedSession(
            MockHttpSession session,
            String headerName,
            String token,
            String preLoginToken
    ) {
    }
}
