package com.nh.customermanager.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "customers",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_customers_email",
                        columnNames = "email"
                ),
                @UniqueConstraint(
                        name = "uk_customers_phone",
                        columnNames = "phone"
                )
        }
)
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "客户姓名不能为空")
    @Size(
            min = 2,
            max = 50,
            message = "客户姓名长度必须在2到50个字符之间"
    )
    @Column(nullable = false, length = 100)
    private String name;

    @Pattern(
            regexp = "^$|^(?=.{6,30}$)(?=.*\\d)[0-9+()\\-\\s]+$",
            message = "电话号码格式不正确，只能包含数字、空格、加号、括号和横线"
    )
    @Column(length = 30)
    private String phone;

    @Email(message = "邮箱格式不正确")
    @Pattern(
            regexp = "^$|^[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(?:\\.[A-Za-z0-9-]+)*\\.[A-Za-z]{2,63}$",
            message = "邮箱格式不正确"
    )
    @Size(max = 150, message = "邮箱长度不能超过150个字符")
    @Column(length = 150)
    private String email;

    @NotBlank(message = "客户状态不能为空")
    @Pattern(
            regexp = "^(POTENTIAL|ACTIVE)$",
            message = "客户状态只能是潜在客户或正式客户"
    )
    @Column(nullable = false, length = 30)
    private String status = "POTENTIAL";

    @Column(
            name = "created_at",
            nullable = false,
            updatable = false
    )
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
