package com.nh.customermanager.repository;

import com.nh.customermanager.entity.Customer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByPhone(String phone);

    boolean existsByEmailIgnoreCaseAndIdNot(
            String email,
            Long id
    );

    boolean existsByPhoneAndIdNot(
            String phone,
            Long id
    );

    @Query("""
            SELECT customer
            FROM Customer customer
            WHERE (
                :keyword = ''
                OR LOWER(customer.name) LIKE LOWER(
                    CONCAT('%', :keyword, '%')
                )
            )
            AND (
                :status = ''
                OR customer.status = :status
            )
            """)
    Page<Customer> search(
            @Param("keyword") String keyword,
            @Param("status") String status,
            Pageable pageable
    );
}