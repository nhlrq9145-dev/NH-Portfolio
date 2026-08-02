package com.nh.customermanager;

import com.nh.customermanager.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTests {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new DatabaseConflictController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void emailDatabaseConflictReturnsConflict() throws Exception {
        expectConflict("/test/database-conflicts/email", "邮箱已存在");
    }

    @Test
    void phoneDatabaseConflictReturnsConflict() throws Exception {
        expectConflict("/test/database-conflicts/phone", "电话已存在");
    }

    @Test
    void unknownDatabaseConflictReturnsGenericConflict()
            throws Exception {
        expectConflict(
                "/test/database-conflicts/unknown",
                "数据冲突，请检查邮箱或电话是否已存在"
        );
    }

    private void expectConflict(String path, String message)
            throws Exception {
        mockMvc.perform(get(path))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value(message))
                .andExpect(jsonPath("$.sql").doesNotExist())
                .andExpect(jsonPath("$.exception").doesNotExist())
                .andExpect(jsonPath("$.stackTrace").doesNotExist());
    }

    @RestController
    static class DatabaseConflictController {

        @GetMapping("/test/database-conflicts/email")
        void emailConflict() {
            throw databaseConflict(
                    "Unique constraint UK_CUSTOMERS_EMAIL was violated"
            );
        }

        @GetMapping("/test/database-conflicts/phone")
        void phoneConflict() {
            throw databaseConflict(
                    "Duplicate entry for key 'uk_customers_phone'"
            );
        }

        @GetMapping("/test/database-conflicts/unknown")
        void unknownConflict() {
            throw databaseConflict("Unrecognized integrity constraint");
        }

        private static DataIntegrityViolationException databaseConflict(
                String databaseMessage
        ) {
            return new DataIntegrityViolationException(
                    "Database write failed",
                    new IllegalStateException(databaseMessage)
            );
        }
    }
}
