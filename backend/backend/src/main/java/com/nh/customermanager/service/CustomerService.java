package com.nh.customermanager.service;

import com.nh.customermanager.entity.Customer;
import com.nh.customermanager.repository.CustomerRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;
import java.util.Set;

@Service
public class CustomerService {

    private final CustomerRepository customerRepository;

    public CustomerService(
            CustomerRepository customerRepository
    ) {
        this.customerRepository = customerRepository;
    }

    public Page<Customer> findAll(
            int page,
            int size,
            String keyword,
            String status
    ) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);

        String normalizedKeyword = normalizeSearchValue(keyword);
        String normalizedStatus = normalizeStatus(status);

        Pageable pageable = PageRequest.of(
                safePage,
                safeSize,
                Sort.by(Sort.Direction.DESC, "id")
        );

        return customerRepository.search(
                normalizedKeyword,
                normalizedStatus,
                pageable
        );
    }

    public Customer findById(Long id) {
        return customerRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "客户不存在，ID：" + id
                ));
    }

    public Customer create(Customer customer) {
        customer.setId(null);
        normalizeCustomer(customer);

        if (
                customer.getEmail() != null
                && customerRepository.existsByEmailIgnoreCase(
                        customer.getEmail()
                )
        ) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "该邮箱已被其他客户使用"
            );
        }

        if (
                customer.getPhone() != null
                && customerRepository.existsByPhone(
                        customer.getPhone()
                )
        ) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "该手机号已被其他客户使用"
            );
        }

        return customerRepository.save(customer);
    }

    public Customer update(
            Long id,
            Customer newCustomer
    ) {
        Customer customer = findById(id);

        customer.setName(newCustomer.getName());
        customer.setPhone(newCustomer.getPhone());
        customer.setEmail(newCustomer.getEmail());
        customer.setStatus(newCustomer.getStatus());

        normalizeCustomer(customer);

        if (
                customer.getEmail() != null
                && customerRepository
                        .existsByEmailIgnoreCaseAndIdNot(
                                customer.getEmail(),
                                id
                        )
        ) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "该邮箱已被其他客户使用"
            );
        }

        if (
                customer.getPhone() != null
                && customerRepository.existsByPhoneAndIdNot(
                        customer.getPhone(),
                        id
                )
        ) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "该手机号已被其他客户使用"
            );
        }

        return customerRepository.save(customer);
    }

    public void delete(Long id) {
        Customer customer = findById(id);
        customerRepository.delete(customer);
    }

    private void normalizeCustomer(Customer customer) {
        customer.setName(customer.getName().trim());
        customer.setPhone(normalizeOptional(customer.getPhone()));
        customer.setEmail(normalizeOptional(customer.getEmail()));
    }

    private String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }

    private String normalizeSearchValue(String value) {
        if (value == null) {
            return "";
        }

        return value.trim();
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return "";
        }

        String normalizedStatus = status
                .trim()
                .toUpperCase(Locale.ROOT);

        Set<String> allowedStatuses = Set.of(
                "POTENTIAL",
                "ACTIVE"
        );

        if (!allowedStatuses.contains(normalizedStatus)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "客户状态只能是 POTENTIAL 或 ACTIVE"
            );
        }

        return normalizedStatus;
    }
}
