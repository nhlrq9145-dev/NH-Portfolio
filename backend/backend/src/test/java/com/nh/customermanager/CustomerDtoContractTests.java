package com.nh.customermanager;

import com.nh.customermanager.controller.CustomerController;
import com.nh.customermanager.entity.Customer;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomerDtoContractTests {

    private static final String DTO_PACKAGE =
            "com.nh.customermanager.dto.";

    @Test
    void customerRequestHasExactInputContract() throws Exception {
        Class<?> requestType = dtoType("CustomerRequest");

        assertTrue(requestType.isRecord());
        assertEquals(
                List.of("name", "phone", "email", "status"),
                componentNames(requestType)
        );
    }

    @Test
    void customerResponseHasExactAdminContract() throws Exception {
        Class<?> responseType = dtoType("CustomerResponse");

        assertTrue(responseType.isRecord());
        assertEquals(
                List.of(
                        "id",
                        "name",
                        "phone",
                        "email",
                        "status",
                        "createdAt"
                ),
                componentNames(responseType)
        );
    }

    @Test
    void customerPageResponseKeepsCurrentPaginationContract()
            throws Exception {
        Class<?> pageType = dtoType("CustomerPageResponse");

        assertTrue(pageType.isRecord());
        assertEquals(
                List.of(
                        "content",
                        "page",
                        "size",
                        "totalElements",
                        "totalPages",
                        "first",
                        "last"
                ),
                componentNames(pageType)
        );

        ParameterizedType contentType = assertInstanceOf(
                ParameterizedType.class,
                pageType.getRecordComponents()[0].getGenericType()
        );
        assertEquals(
                DTO_PACKAGE + "CustomerResponse",
                contentType.getActualTypeArguments()[0].getTypeName()
        );
    }

    @Test
    void controllerPublicMethodsUseDtosAtCustomerBoundary()
            throws Exception {
        Class<?> requestType = dtoType("CustomerRequest");
        Class<?> responseType = dtoType("CustomerResponse");
        Class<?> pageType = dtoType("CustomerPageResponse");

        assertEquals(
                pageType,
                CustomerController.class.getDeclaredMethod(
                        "findAll",
                        int.class,
                        int.class,
                        String.class,
                        String.class
                ).getReturnType()
        );
        assertEquals(
                responseType,
                CustomerController.class.getDeclaredMethod(
                        "findById",
                        Long.class
                ).getReturnType()
        );
        assertEquals(
                responseType,
                CustomerController.class.getDeclaredMethod(
                        "create",
                        requestType
                ).getReturnType()
        );
        assertEquals(
                responseType,
                CustomerController.class.getDeclaredMethod(
                        "update",
                        Long.class,
                        requestType
                ).getReturnType()
        );

        for (Method method : CustomerController.class
                .getDeclaredMethods()) {
            if (!Modifier.isPublic(method.getModifiers())) {
                continue;
            }

            assertFalse(
                    method.getReturnType().equals(Customer.class),
                    method.getName() + " must not return Customer"
            );
            assertFalse(
                    Arrays.asList(method.getParameterTypes())
                            .contains(Customer.class),
                    method.getName() + " must not accept Customer"
            );
        }
    }

    private Class<?> dtoType(String simpleName)
            throws ClassNotFoundException {
        return Class.forName(DTO_PACKAGE + simpleName);
    }

    private List<String> componentNames(Class<?> recordType) {
        return Arrays.stream(recordType.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();
    }
}
