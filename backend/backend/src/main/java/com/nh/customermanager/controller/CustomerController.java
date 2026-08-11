package com.nh.customermanager.controller;

import com.nh.customermanager.dto.CustomerPageResponse;
import com.nh.customermanager.dto.CustomerRequest;
import com.nh.customermanager.dto.CustomerResponse;
import com.nh.customermanager.entity.Customer;
import com.nh.customermanager.service.CustomerService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/customers")
public class CustomerController {

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @GetMapping
    public CustomerPageResponse findAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "") String status
    ) {
        Page<Customer> result = customerService.findAll(
                page,
                size,
                keyword,
                status
        );

        return new CustomerPageResponse(
                result.getContent()
                        .stream()
                        .map(this::toResponse)
                        .toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.isFirst(),
                result.isLast()
        );
    }

    @GetMapping("/{id}")
    public CustomerResponse findById(@PathVariable Long id) {
        return toResponse(customerService.findById(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CustomerResponse create(
            @Valid @RequestBody CustomerRequest request
    ) {
        return toResponse(customerService.create(toEntity(request)));
    }

    @PutMapping("/{id}")
    public CustomerResponse update(
            @PathVariable Long id,
            @Valid @RequestBody CustomerRequest request
    ) {
        return toResponse(customerService.update(id, toEntity(request)));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        customerService.delete(id);
    }

    private Customer toEntity(CustomerRequest request) {
        Customer customer = new Customer();
        customer.setName(request.name());
        customer.setPhone(request.phone());
        customer.setEmail(request.email());
        customer.setStatus(request.status());
        return customer;
    }

    private CustomerResponse toResponse(Customer customer) {
        return new CustomerResponse(
                customer.getId(),
                customer.getName(),
                customer.getPhone(),
                customer.getEmail(),
                customer.getStatus(),
                customer.getCreatedAt()
        );
    }
}
