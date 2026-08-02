package com.nh.customermanager;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.sql.DataSource;
import java.sql.Connection;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
        }
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
    void wrongPasswordReturnsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/auth/login")
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
        MockHttpSession session = loginAndGetSession();

        assertFalse(session.isInvalid());
        assertNotNull(session.getAttribute(
                HttpSessionSecurityContextRepository
                        .SPRING_SECURITY_CONTEXT_KEY
        ));
    }

    @Test
    void authenticatedSessionCanAccessCurrentUser()
            throws Exception {
        MockHttpSession session = loginAndGetSession();

        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.username").value(
                        "auth-test-only-admin"
                ));
    }

    @Test
    void authenticatedSessionCanAccessCustomers()
            throws Exception {
        MockHttpSession session = loginAndGetSession();

        mockMvc.perform(get("/api/customers").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void logoutInvalidatesSessionAndBlocksProtectedRequest()
            throws Exception {
        MockHttpSession session = loginAndGetSession();

        mockMvc.perform(post("/api/auth/logout").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("退出成功"));

        assertTrue(session.isInvalid());

        mockMvc.perform(get("/api/customers").session(session))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    private MockHttpSession loginAndGetSession()
            throws Exception {
        MvcResult result = mockMvc.perform(
                        post("/api/auth/login")
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

        return (MockHttpSession) result
                .getRequest()
                .getSession(false);
    }

}
