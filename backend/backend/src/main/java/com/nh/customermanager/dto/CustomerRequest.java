package com.nh.customermanager.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.deser.jdk.StringDeserializer;

public record CustomerRequest(
        @NotBlank(message = "客户姓名不能为空")
        @Size(
                min = 2,
                max = 50,
                message = "客户姓名长度必须在2到50个字符之间"
        )
        String name,

        @Pattern(
                regexp = "^$|^(?=.{6,30}$)(?=.*\\d)[0-9+()\\-\\s]+$",
                message = "电话号码格式不正确，只能包含数字、空格、加号、括号和横线"
        )
        String phone,

        @Email(message = "邮箱格式不正确")
        @Pattern(
                regexp = "^$|^[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(?:\\.[A-Za-z0-9-]+)*\\.[A-Za-z]{2,63}$",
                message = "邮箱格式不正确"
        )
        @Size(max = 150, message = "邮箱长度不能超过150个字符")
        String email,

        @NotBlank(message = "客户状态不能为空")
        @Pattern(
                regexp = "^(POTENTIAL|ACTIVE)$",
                message = "客户状态只能是潜在客户或正式客户"
        )
        @JsonDeserialize(using = StatusDeserializer.class)
        String status
) {
    public static final class StatusDeserializer
            extends StringDeserializer {

        @Override
        public String getAbsentValue(
                DeserializationContext context
        ) {
            return "POTENTIAL";
        }
    }
}
