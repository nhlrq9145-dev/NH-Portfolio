package com.nh.customermanager;

import com.jayway.jsonpath.JsonPath;
import com.nh.customermanager.entity.Customer;
import com.nh.customermanager.repository.CustomerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
class CustomerApiIntegrationTests {

    private static final Set<String> CUSTOMER_RESPONSE_FIELDS = Set.of(
            "id",
            "name",
            "phone",
            "email",
            "status",
            "createdAt"
    );

    private static final Set<String> CUSTOMER_PAGE_RESPONSE_FIELDS = Set.of(
            "content",
            "page",
            "size",
            "totalElements",
            "totalPages",
            "first",
            "last"
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
    private CustomerRepository customerRepository;

    @Autowired
    private DataSource dataSource;

    private MockHttpSession session;

    private String csrfHeaderName;

    private String csrfToken;

    @BeforeEach
    void cleanCustomersAndLogin() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            assertTrue(
                    connection.getMetaData()
                            .getURL()
                            .startsWith(
                                    "jdbc:h2:mem:auth_integration_test"
                            )
            );
        }

        customerRepository.deleteAllInBatch();
        session = loginAndGetSession();
    }

    @Test
    void createsReadsUpdatesAndDeletesCustomer() throws Exception {
        MvcResult createResult = mockMvc.perform(
                        post("/api/customers")
                                .session(session)
                                .header(csrfHeaderName, csrfToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(customerJson(
                                        "Alice Chen",
                                        "+81 90-1234-5678",
                                        "alice@example.com",
                                        "POTENTIAL"
                                ))
                )
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("Alice Chen"))
                .andExpect(jsonPath("$.phone").value(
                        "+81 90-1234-5678"
                ))
                .andExpect(jsonPath("$.email").value(
                        "alice@example.com"
                ))
                .andExpect(jsonPath("$.status").value("POTENTIAL"))
                .andExpect(jsonPath("$.createdAt").isString())
                .andReturn();

        long customerId = readId(createResult);

        mockMvc.perform(
                        get("/api/customers/{id}", customerId)
                                .session(session)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(customerId))
                .andExpect(jsonPath("$.name").value("Alice Chen"))
                .andExpect(jsonPath("$.phone").value(
                        "+81 90-1234-5678"
                ))
                .andExpect(jsonPath("$.email").value(
                        "alice@example.com"
                ))
                .andExpect(jsonPath("$.status").value("POTENTIAL"));

        mockMvc.perform(
                        put("/api/customers/{id}", customerId)
                                .session(session)
                                .header(csrfHeaderName, csrfToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(customerJson(
                                        "Alice Updated",
                                        "090-9876-5432",
                                        "alice.updated@example.com",
                                        "ACTIVE"
                                ))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(customerId))
                .andExpect(jsonPath("$.name").value(
                        "Alice Updated"
                ))
                .andExpect(jsonPath("$.phone").value(
                        "090-9876-5432"
                ))
                .andExpect(jsonPath("$.email").value(
                        "alice.updated@example.com"
                ))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        Customer updated = customerRepository.findById(customerId)
                .orElseThrow();
        assertEquals("Alice Updated", updated.getName());
        assertEquals("090-9876-5432", updated.getPhone());
        assertEquals(
                "alice.updated@example.com",
                updated.getEmail()
        );
        assertEquals("ACTIVE", updated.getStatus());

        mockMvc.perform(
                        delete("/api/customers/{id}", customerId)
                                .session(session)
                                .header(csrfHeaderName, csrfToken)
                )
                .andExpect(status().isNoContent());

        mockMvc.perform(
                        get("/api/customers/{id}", customerId)
                                .session(session)
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value(
                        "客户不存在，ID：" + customerId
                ));
    }

    @Test
    void createRejectsMissingForgedAndCrossSessionCsrfTokens()
            throws Exception {
        String requestJson = customerJson(
                "Rejected Create",
                "090-9000-0001",
                "rejected-create@example.com",
                "POTENTIAL"
        );
        long customerCount = customerRepository.count();
        CsrfSession otherSession = getCsrfSession();

        expectCsrfForbidden(
                post("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson)
        );
        expectCsrfForbidden(
                post("/api/customers")
                        .header(csrfHeaderName, "forged-test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson)
        );
        expectCsrfForbidden(
                post("/api/customers")
                        .header(
                                otherSession.headerName(),
                                otherSession.token()
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson)
        );

        assertEquals(customerCount, customerRepository.count());
    }

    @Test
    void updateRejectsMissingForgedAndCrossSessionCsrfTokens()
            throws Exception {
        long customerId = createCustomer(
                "Protected Update",
                "090-9000-0002",
                "protected-update@example.com",
                "POTENTIAL"
        );
        String requestJson = customerJson(
                "Rejected Update",
                "090-9000-0003",
                "rejected-update@example.com",
                "ACTIVE"
        );
        CsrfSession otherSession = getCsrfSession();

        expectCsrfForbidden(
                put("/api/customers/{id}", customerId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson)
        );
        expectCsrfForbidden(
                put("/api/customers/{id}", customerId)
                        .header(csrfHeaderName, "forged-test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson)
        );
        expectCsrfForbidden(
                put("/api/customers/{id}", customerId)
                        .header(
                                otherSession.headerName(),
                                otherSession.token()
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson)
        );

        Customer unchanged = customerRepository.findById(customerId)
                .orElseThrow();
        assertEquals("Protected Update", unchanged.getName());
        assertEquals("POTENTIAL", unchanged.getStatus());
    }

    @Test
    void deleteRejectsMissingForgedAndCrossSessionCsrfTokens()
            throws Exception {
        long customerId = createCustomer(
                "Protected Delete",
                "090-9000-0004",
                "protected-delete@example.com",
                "POTENTIAL"
        );
        CsrfSession otherSession = getCsrfSession();

        expectCsrfForbidden(
                delete("/api/customers/{id}", customerId)
        );
        expectCsrfForbidden(
                delete("/api/customers/{id}", customerId)
                        .header(csrfHeaderName, "forged-test-token")
        );
        expectCsrfForbidden(
                delete("/api/customers/{id}", customerId)
                        .header(
                                otherSession.headerName(),
                                otherSession.token()
                        )
        );

        assertTrue(customerRepository.existsById(customerId));
    }

    @Test
    void createRequestCannotControlServerManagedFields()
            throws Exception {
        MvcResult result = mockMvc.perform(
                        post("/api/customers")
                                .session(session)
                                .header(csrfHeaderName, csrfToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "id": 999999,
                                          "name": "DTO Request",
                                          "phone": "090-0000-0001",
                                          "email": "dto-request@example.com",
                                          "status": "POTENTIAL",
                                          "createdAt": "2000-01-01T00:00:00"
                                        }
                                        """)
                )
                .andExpect(status().isCreated())
                .andReturn();

        Map<String, Object> response = readObject(result);
        assertEquals(CUSTOMER_RESPONSE_FIELDS, response.keySet());
        assertFalse(
                ((Number) response.get("id")).longValue() == 999999L
        );
        assertFalse(
                "2000-01-01T00:00:00".equals(
                        response.get("createdAt")
                )
        );
    }

    @Test
    void customerEndpointsKeepExactAdminJsonContracts()
            throws Exception {
        long customerId = createCustomer(
                "DTO Contract",
                "090-0000-0002",
                "dto-contract@example.com",
                "POTENTIAL"
        );

        MvcResult findResult = mockMvc.perform(
                        get("/api/customers/{id}", customerId)
                                .session(session)
                )
                .andExpect(status().isOk())
                .andReturn();
        assertEquals(
                CUSTOMER_RESPONSE_FIELDS,
                readObject(findResult).keySet()
        );

        MvcResult updateResult = mockMvc.perform(
                        put("/api/customers/{id}", customerId)
                                .session(session)
                                .header(csrfHeaderName, csrfToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(customerJson(
                                        "DTO Contract Updated",
                                        "090-0000-0003",
                                        "dto-contract-updated@example.com",
                                        "ACTIVE"
                                ))
                )
                .andExpect(status().isOk())
                .andReturn();
        assertEquals(
                CUSTOMER_RESPONSE_FIELDS,
                readObject(updateResult).keySet()
        );

        MvcResult pageResult = mockMvc.perform(
                        get("/api/customers").session(session)
                )
                .andExpect(status().isOk())
                .andReturn();
        Map<String, Object> page = readObject(pageResult);
        assertEquals(CUSTOMER_PAGE_RESPONSE_FIELDS, page.keySet());

        List<Map<String, Object>> content = JsonPath.read(
                pageResult.getResponse().getContentAsString(),
                "$.content"
        );
        assertEquals(1, content.size());
        assertEquals(
                CUSTOMER_RESPONSE_FIELDS,
                content.get(0).keySet()
        );
    }

    @Test
    void omittedStatusDefaultsToPotentialOnCreate()
            throws Exception {
        MvcResult result = mockMvc.perform(
                        post("/api/customers")
                                .session(session)
                                .header(csrfHeaderName, csrfToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(customerJsonWithoutStatus(
                                        "Default Create Status",
                                        "090-0000-0004",
                                        "default-create-status@example.com"
                                ))
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("POTENTIAL"))
                .andReturn();

        Customer customer = customerRepository.findById(readId(result))
                .orElseThrow();
        assertEquals("POTENTIAL", customer.getStatus());
    }

    @Test
    void omittedStatusDefaultsToPotentialOnUpdate()
            throws Exception {
        long customerId = createCustomer(
                "Default Update Source",
                "090-0000-0005",
                "default-update-source@example.com",
                "ACTIVE"
        );

        mockMvc.perform(
                        put("/api/customers/{id}", customerId)
                                .session(session)
                                .header(csrfHeaderName, csrfToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(customerJsonWithoutStatus(
                                        "Default Update Status",
                                        "090-0000-0006",
                                        "default-update-status@example.com"
                                ))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(customerId))
                .andExpect(jsonPath("$.status").value("POTENTIAL"));

        Customer customer = customerRepository.findById(customerId)
                .orElseThrow();
        assertEquals("POTENTIAL", customer.getStatus());
    }

    @Test
    void nullStatusOnCreateReturnsValidationErrorWithoutCreatingCustomer()
            throws Exception {
        long customerCount = customerRepository.count();

        expectValidationError(
                customerJsonWithNullStatus(
                        "Null Create Status",
                        "090-0000-0007",
                        "null-create-status@example.com"
                ),
                "status"
        );

        assertEquals(customerCount, customerRepository.count());
    }

    @Test
    void nullStatusOnUpdateLeavesCustomerUnchanged()
            throws Exception {
        long customerId = createCustomer(
                "Null Update Source",
                "090-0000-0008",
                "null-update-source@example.com",
                "ACTIVE"
        );
        Customer before = customerRepository.findById(customerId)
                .orElseThrow();

        expectUpdateValidationError(
                customerId,
                customerJsonWithNullStatus(
                        "Null Update Attempt",
                        "090-0000-0009",
                        "null-update-attempt@example.com"
                ),
                "status"
        );

        Customer after = customerRepository.findById(customerId)
                .orElseThrow();
        assertEquals(before.getId(), after.getId());
        assertEquals(before.getName(), after.getName());
        assertEquals(before.getPhone(), after.getPhone());
        assertEquals(before.getEmail(), after.getEmail());
        assertEquals("ACTIVE", after.getStatus());
        assertEquals(before.getCreatedAt(), after.getCreatedAt());
    }

    @Test
    void blankNameReturnsValidationError() throws Exception {
        expectValidationError(
                customerJson(
                        " ",
                        "090-1000-0001",
                        "blank-name@example.com",
                        "POTENTIAL"
                ),
                "name"
        );
    }

    @Test
    void tooShortNameReturnsValidationError() throws Exception {
        expectValidationError(
                customerJson(
                        "A",
                        "090-1000-0002",
                        "short-name@example.com",
                        "POTENTIAL"
                ),
                "name"
        );
    }

    @Test
    void tooLongNameReturnsValidationError() throws Exception {
        expectValidationError(
                customerJson(
                        "A".repeat(51),
                        "090-1000-0003",
                        "long-name@example.com",
                        "POTENTIAL"
                ),
                "name"
        );
    }

    @Test
    void invalidEmailReturnsValidationError() throws Exception {
        expectValidationError(
                customerJson(
                        "Invalid Email",
                        "090-1000-0004",
                        "not-an-email",
                        "POTENTIAL"
                ),
                "email"
        );
    }

    @Test
    void invalidPhoneReturnsValidationError() throws Exception {
        expectValidationError(
                customerJson(
                        "Invalid Phone",
                        "phone-only",
                        "invalid-phone@example.com",
                        "POTENTIAL"
                ),
                "phone"
        );
    }

    @Test
    void invalidBodyStatusReturnsValidationError() throws Exception {
        expectValidationError(
                customerJson(
                        "Invalid Status",
                        "090-1000-0005",
                        "invalid-status@example.com",
                        "INACTIVE"
                ),
                "status"
        );
    }

    @Test
    void blankStatusReturnsValidationError() throws Exception {
        expectValidationError(
                customerJson(
                        "Blank Status",
                        "090-1000-0006",
                        "blank-status@example.com",
                        ""
                ),
                "status"
        );
        expectValidationError(
                customerJson(
                        "Whitespace Status",
                        "090-1000-0011",
                        "whitespace-status@example.com",
                        "   "
                ),
                "status"
        );
    }

    @Test
    void blankStatusOnUpdateReturnsValidationError()
            throws Exception {
        long customerId = createCustomer(
                "Blank Update Status Source",
                "090-1000-0007",
                "blank-update-status-source@example.com",
                "ACTIVE"
        );

        expectUpdateValidationError(
                customerId,
                customerJson(
                        "Blank Update Status",
                        "090-1000-0008",
                        "blank-update-status@example.com",
                        ""
                ),
                "status"
        );
        expectUpdateValidationError(
                customerId,
                customerJson(
                        "Whitespace Update Status",
                        "090-1000-0012",
                        "whitespace-update-status@example.com",
                        "   "
                ),
                "status"
        );
    }

    @Test
    void invalidStatusOnUpdateReturnsValidationError()
            throws Exception {
        long customerId = createCustomer(
                "Invalid Update Status Source",
                "090-1000-0009",
                "invalid-update-status-source@example.com",
                "ACTIVE"
        );

        expectUpdateValidationError(
                customerId,
                customerJson(
                        "Invalid Update Status",
                        "090-1000-0010",
                        "invalid-update-status@example.com",
                        "INACTIVE"
                ),
                "status"
        );
    }

    @Test
    void getMissingCustomerReturnsNotFound() throws Exception {
        long missingId = createAndDeleteCustomer(
                "Missing Get",
                "090-2000-0001",
                "missing-get@example.com"
        );

        expectMissingCustomer(
                get("/api/customers/{id}", missingId),
                missingId
        );
    }

    @Test
    void updateMissingCustomerReturnsNotFound() throws Exception {
        long missingId = createAndDeleteCustomer(
                "Missing Update",
                "090-2000-0002",
                "missing-update@example.com"
        );

        mockMvc.perform(
                        put("/api/customers/{id}", missingId)
                                .session(session)
                                .header(csrfHeaderName, csrfToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(customerJson(
                                        "Updated Missing",
                                        "090-2000-0012",
                                        "updated-missing@example.com",
                                        "ACTIVE"
                                ))
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value(
                        "客户不存在，ID：" + missingId
                ));
    }

    @Test
    void deleteMissingCustomerReturnsNotFound() throws Exception {
        long missingId = createAndDeleteCustomer(
                "Missing Delete",
                "090-2000-0003",
                "missing-delete@example.com"
        );

        expectMissingCustomer(
                delete("/api/customers/{id}", missingId)
                        .header(csrfHeaderName, csrfToken),
                missingId
        );
    }

    @Test
    void duplicateEmailReturnsConflict() throws Exception {
        createCustomer(
                "Email Owner",
                "090-3000-0001",
                "duplicate@example.com",
                "POTENTIAL"
        );

        expectConflict(customerJson(
                "Email Duplicate",
                "090-3000-0002",
                "duplicate@example.com",
                "ACTIVE"
        ));
    }

    @Test
    void caseInsensitiveDuplicateEmailReturnsConflict()
            throws Exception {
        createCustomer(
                "Email Case Owner",
                "090-3000-0003",
                "case@example.com",
                "POTENTIAL"
        );

        expectConflict(customerJson(
                "Email Case Duplicate",
                "090-3000-0004",
                "CASE@EXAMPLE.COM",
                "ACTIVE"
        ));
    }

    @Test
    void duplicatePhoneReturnsConflict() throws Exception {
        createCustomer(
                "Phone Owner",
                "090-3000-0005",
                "phone-owner@example.com",
                "POTENTIAL"
        );

        expectConflict(customerJson(
                "Phone Duplicate",
                "090-3000-0005",
                "phone-duplicate@example.com",
                "ACTIVE"
        ));
    }

    @Test
    void h2RejectsDuplicateEmail() {
        customerRepository.saveAndFlush(customerForPersistence(
                "Database Email Owner",
                "090-3100-0001",
                "database-email@example.com"
        ));

        assertThrows(
                DataIntegrityViolationException.class,
                () -> customerRepository.saveAndFlush(
                        customerForPersistence(
                                "Database Email Duplicate",
                                "090-3100-0002",
                                "database-email@example.com"
                        )
                )
        );
    }

    @Test
    void h2RejectsCaseInsensitiveDuplicateEmail() {
        customerRepository.saveAndFlush(customerForPersistence(
                "Database Email Case Owner",
                "090-3100-0003",
                "database-email-case@example.com"
        ));

        assertThrows(
                DataIntegrityViolationException.class,
                () -> customerRepository.saveAndFlush(
                        customerForPersistence(
                                "Database Email Case Duplicate",
                                "090-3100-0004",
                                "DATABASE-EMAIL-CASE@EXAMPLE.COM"
                        )
                )
        );
    }

    @Test
    void h2RejectsDuplicatePhone() {
        customerRepository.saveAndFlush(customerForPersistence(
                "Database Phone Owner",
                "090-3100-0005",
                "database-phone-owner@example.com"
        ));

        assertThrows(
                DataIntegrityViolationException.class,
                () -> customerRepository.saveAndFlush(
                        customerForPersistence(
                                "Database Phone Duplicate",
                                "090-3100-0005",
                                "database-phone-duplicate@example.com"
                        )
                )
        );
    }

    @Test
    void updateCanRetainOwnEmailAndPhone() throws Exception {
        long customerId = createCustomer(
                "Own Contact",
                "090-4000-0001",
                "own-contact@example.com",
                "POTENTIAL"
        );

        mockMvc.perform(
                        put("/api/customers/{id}", customerId)
                                .session(session)
                                .header(csrfHeaderName, csrfToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(customerJson(
                                        "Own Contact Updated",
                                        "090-4000-0001",
                                        "own-contact@example.com",
                                        "ACTIVE"
                                ))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(customerId))
                .andExpect(jsonPath("$.name").value(
                        "Own Contact Updated"
                ))
                .andExpect(jsonPath("$.phone").value(
                        "090-4000-0001"
                ))
                .andExpect(jsonPath("$.email").value(
                        "own-contact@example.com"
                ))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void updateToAnotherCustomersEmailReturnsConflict()
            throws Exception {
        long customerId = createCustomer(
                "Email Update Source",
                "090-4100-0001",
                "email-update-source@example.com",
                "POTENTIAL"
        );
        createCustomer(
                "Email Update Owner",
                "090-4100-0002",
                "email-update-owner@example.com",
                "ACTIVE"
        );

        mockMvc.perform(
                        put("/api/customers/{id}", customerId)
                                .session(session)
                                .header(csrfHeaderName, csrfToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(customerJson(
                                        "Email Update Source",
                                        "090-4100-0001",
                                        "email-update-owner@example.com",
                                        "ACTIVE"
                                ))
                )
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value(
                        "该邮箱已被其他客户使用"
                ));
    }

    @Test
    void updateToAnotherCustomersPhoneReturnsConflict()
            throws Exception {
        long customerId = createCustomer(
                "Phone Update Source",
                "090-4200-0001",
                "phone-update-source@example.com",
                "POTENTIAL"
        );
        createCustomer(
                "Phone Update Owner",
                "090-4200-0002",
                "phone-update-owner@example.com",
                "ACTIVE"
        );

        mockMvc.perform(
                        put("/api/customers/{id}", customerId)
                                .session(session)
                                .header(csrfHeaderName, csrfToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(customerJson(
                                        "Phone Update Source",
                                        "090-4200-0002",
                                        "phone-update-source@example.com",
                                        "ACTIVE"
                                ))
                )
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value(
                        "该手机号已被其他客户使用"
                ));
    }

    @Test
    void defaultPageHasActualFieldsAndIdDescendingOrder()
            throws Exception {
        long firstId = createCustomer(
                "Default First",
                "090-5000-0001",
                "default-first@example.com",
                "POTENTIAL"
        );
        long secondId = createCustomer(
                "Default Second",
                "090-5000-0002",
                "default-second@example.com",
                "ACTIVE"
        );
        long thirdId = createCustomer(
                "Default Third",
                "090-5000-0003",
                "default-third@example.com",
                "POTENTIAL"
        );

        MvcResult result = mockMvc.perform(
                        get("/api/customers").session(session)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.first").value(true))
                .andExpect(jsonPath("$.last").value(true))
                .andReturn();

        assertEquals(
                List.of(thirdId, secondId, firstId),
                readIds(result)
        );
    }

    @Test
    void pageAndSizeSelectExpectedSlice() throws Exception {
        long firstId = createCustomer(
                "Page First",
                "090-5100-0001",
                "page-first@example.com",
                "POTENTIAL"
        );
        long secondId = createCustomer(
                "Page Second",
                "090-5100-0002",
                "page-second@example.com",
                "POTENTIAL"
        );
        long thirdId = createCustomer(
                "Page Third",
                "090-5100-0003",
                "page-third@example.com",
                "POTENTIAL"
        );
        createCustomer(
                "Page Fourth",
                "090-5100-0004",
                "page-fourth@example.com",
                "POTENTIAL"
        );
        createCustomer(
                "Page Fifth",
                "090-5100-0005",
                "page-fifth@example.com",
                "POTENTIAL"
        );

        MvcResult result = mockMvc.perform(
                        get("/api/customers")
                                .session(session)
                                .param("page", "1")
                                .param("size", "2")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.first").value(false))
                .andExpect(jsonPath("$.last").value(false))
                .andReturn();

        assertEquals(List.of(thirdId, secondId), readIds(result));
        assertTrue(firstId < secondId);
    }

    @Test
    void keywordSearchesNameBySubstring() throws Exception {
        createCustomer(
                "Alice North",
                "090-5200-0001",
                "alice-north@example.com",
                "POTENTIAL"
        );
        createCustomer(
                "Beta South",
                "090-5200-0002",
                "beta-south@example.com",
                "ACTIVE"
        );
        createCustomer(
                "Alicia East",
                "090-5200-0003",
                "alicia-east@example.com",
                "ACTIVE"
        );

        MvcResult result = mockMvc.perform(
                        get("/api/customers")
                                .session(session)
                                .param("keyword", "ali")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andReturn();

        assertEquals(
                List.of("Alicia East", "Alice North"),
                readNames(result)
        );
    }

    @Test
    void statusFiltersPotentialAndActive() throws Exception {
        createCustomer(
                "Potential Customer",
                "090-5300-0001",
                "potential@example.com",
                "POTENTIAL"
        );
        createCustomer(
                "Active Customer",
                "090-5300-0002",
                "active@example.com",
                "ACTIVE"
        );

        mockMvc.perform(
                        get("/api/customers")
                                .session(session)
                                .param("status", "POTENTIAL")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath(
                        "$.content[0].name"
                ).value("Potential Customer"))
                .andExpect(jsonPath(
                        "$.content[0].status"
                ).value("POTENTIAL"));

        mockMvc.perform(
                        get("/api/customers")
                                .session(session)
                                .param("status", "ACTIVE")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath(
                        "$.content[0].name"
                ).value("Active Customer"))
                .andExpect(jsonPath(
                        "$.content[0].status"
                ).value("ACTIVE"));
    }

    @Test
    void keywordAndStatusCanBeCombined() throws Exception {
        createCustomer(
                "North Potential",
                "090-5400-0001",
                "north-potential@example.com",
                "POTENTIAL"
        );
        createCustomer(
                "North Active",
                "090-5400-0002",
                "north-active@example.com",
                "ACTIVE"
        );
        createCustomer(
                "South Active",
                "090-5400-0003",
                "south-active@example.com",
                "ACTIVE"
        );

        mockMvc.perform(
                        get("/api/customers")
                                .session(session)
                                .param("keyword", "north")
                                .param("status", "ACTIVE")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath(
                        "$.content[0].name"
                ).value("North Active"))
                .andExpect(jsonPath(
                        "$.content[0].status"
                ).value("ACTIVE"));
    }

    @Test
    void invalidFilterStatusReturnsBadRequest() throws Exception {
        mockMvc.perform(
                        get("/api/customers")
                                .session(session)
                                .param("status", "INACTIVE")
                )
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(
                        "客户状态只能是 POTENTIAL 或 ACTIVE"
                ));
    }

    @Test
    void pageAndSizeUseServiceBoundaries() throws Exception {
        createCustomer(
                "Boundary First",
                "090-5500-0001",
                "boundary-first@example.com",
                "POTENTIAL"
        );
        createCustomer(
                "Boundary Second",
                "090-5500-0002",
                "boundary-second@example.com",
                "ACTIVE"
        );

        mockMvc.perform(
                        get("/api/customers")
                                .session(session)
                                .param("page", "-2")
                                .param("size", "0")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.first").value(true))
                .andExpect(jsonPath("$.last").value(false));

        mockMvc.perform(
                        get("/api/customers")
                                .session(session)
                                .param("size", "101")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(100))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.first").value(true))
                .andExpect(jsonPath("$.last").value(true));
    }

    private MockHttpSession loginAndGetSession() throws Exception {
        CsrfSession preLoginCsrf = getCsrfSession();
        MvcResult result = mockMvc.perform(
                        post("/api/auth/login")
                                .session(preLoginCsrf.session())
                                .header(
                                        preLoginCsrf.headerName(),
                                        preLoginCsrf.token()
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(LOGIN_JSON)
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

        MockHttpSession authenticatedSession =
                (MockHttpSession) result.getRequest().getSession(false);
        assertFalse(authenticatedSession.isInvalid());
        assertNotNull(authenticatedSession.getAttribute(
                HttpSessionSecurityContextRepository
                        .SPRING_SECURITY_CONTEXT_KEY
        ));

        CsrfSession postLoginCsrf = getCsrfSession(
                authenticatedSession
        );
        assertNotEquals(
                preLoginCsrf.token(),
                postLoginCsrf.token()
        );
        csrfHeaderName = postLoginCsrf.headerName();
        csrfToken = postLoginCsrf.token();
        return authenticatedSession;
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
                .andExpect(jsonPath("$.headerName").isNotEmpty())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andReturn();

        assertTrue(
                result.getRequest().getSession(false)
                        instanceof MockHttpSession
        );

        return new CsrfSession(
                (MockHttpSession) result.getRequest().getSession(false),
                JsonPath.read(
                        result.getResponse().getContentAsString(),
                        "$.headerName"
                ),
                JsonPath.read(
                        result.getResponse().getContentAsString(),
                        "$.token"
                )
        );
    }

    private ResultActions expectCsrfForbidden(
            MockHttpServletRequestBuilder request
    ) throws Exception {
        return mockMvc.perform(request.session(session))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value(
                        "请求安全校验失败，请刷新页面后重试"
                ));
    }

    private long createCustomer(
            String name,
            String phone,
            String email,
            String customerStatus
    ) throws Exception {
        MvcResult result = mockMvc.perform(
                        post("/api/customers")
                                .session(session)
                                .header(csrfHeaderName, csrfToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(customerJson(
                                        name,
                                        phone,
                                        email,
                                        customerStatus
                                ))
                )
                .andExpect(status().isCreated())
                .andReturn();

        return readId(result);
    }

    private long createAndDeleteCustomer(
            String name,
            String phone,
            String email
    ) throws Exception {
        long customerId = createCustomer(
                name,
                phone,
                email,
                "POTENTIAL"
        );

        mockMvc.perform(
                        delete("/api/customers/{id}", customerId)
                                .session(session)
                                .header(csrfHeaderName, csrfToken)
                )
                .andExpect(status().isNoContent());

        return customerId;
    }

    private void expectValidationError(
            String requestJson,
            String field
    ) throws Exception {
        mockMvc.perform(
                        post("/api/customers")
                                .session(session)
                                .header(csrfHeaderName, csrfToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestJson)
                )
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(
                        "提交的数据不符合要求"
                ))
                .andExpect(jsonPath("$.errors").isMap())
                .andExpect(jsonPath("$.errors." + field).exists());
    }

    private void expectUpdateValidationError(
            long customerId,
            String requestJson,
            String field
    ) throws Exception {
        mockMvc.perform(
                        put("/api/customers/{id}", customerId)
                                .session(session)
                                .header(csrfHeaderName, csrfToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestJson)
                )
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(
                        "提交的数据不符合要求"
                ))
                .andExpect(jsonPath("$.errors").isMap())
                .andExpect(jsonPath("$.errors." + field).exists());
    }

    private void expectConflict(String requestJson) throws Exception {
        mockMvc.perform(
                        post("/api/customers")
                                .session(session)
                                .header(csrfHeaderName, csrfToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestJson)
                )
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    private void expectMissingCustomer(
            org.springframework.test.web.servlet
                    .request.MockHttpServletRequestBuilder request,
            long customerId
    ) throws Exception {
        mockMvc.perform(request.session(session))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value(
                        "客户不存在，ID：" + customerId
                ));
    }

    private long readId(MvcResult result) throws Exception {
        Number id = JsonPath.read(
                result.getResponse().getContentAsString(),
                "$.id"
        );
        return id.longValue();
    }

    private Map<String, Object> readObject(MvcResult result)
            throws Exception {
        return JsonPath.read(
                result.getResponse().getContentAsString(),
                "$"
        );
    }

    private List<Long> readIds(MvcResult result) throws Exception {
        List<Number> ids = JsonPath.read(
                result.getResponse().getContentAsString(),
                "$.content[*].id"
        );
        return ids.stream().map(Number::longValue).toList();
    }

    private List<String> readNames(MvcResult result)
            throws Exception {
        return JsonPath.read(
                result.getResponse().getContentAsString(),
                "$.content[*].name"
        );
    }

    private Customer customerForPersistence(
            String name,
            String phone,
            String email
    ) {
        Customer customer = new Customer();
        customer.setName(name);
        customer.setPhone(phone);
        customer.setEmail(email);
        customer.setStatus("POTENTIAL");
        return customer;
    }

    private String customerJson(
            String name,
            String phone,
            String email,
            String customerStatus
    ) {
        return """
                {
                  "name": "%s",
                  "phone": "%s",
                  "email": "%s",
                  "status": "%s"
                }
                """.formatted(name, phone, email, customerStatus);
    }

    private String customerJsonWithoutStatus(
            String name,
            String phone,
            String email
    ) {
        return """
                {
                  "name": "%s",
                  "phone": "%s",
                  "email": "%s"
                }
                """.formatted(name, phone, email);
    }

    private String customerJsonWithNullStatus(
            String name,
            String phone,
            String email
    ) {
        return """
                {
                  "name": "%s",
                  "phone": "%s",
                  "email": "%s",
                  "status": null
                }
                """.formatted(name, phone, email);
    }

    private record CsrfSession(
            MockHttpSession session,
            String headerName,
            String token
    ) {
    }
}
