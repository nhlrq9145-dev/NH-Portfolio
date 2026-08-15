package com.nh.customermanager.controller;

import com.nh.customermanager.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    private final CsrfTokenRepository csrfTokenRepository;

    public AuthController(
            AuthService authService,
            CsrfTokenRepository csrfTokenRepository
    ) {
        this.authService = authService;
        this.csrfTokenRepository = csrfTokenRepository;
    }

    @PostMapping("/login")
    public AuthResponse login(
            @Valid @RequestBody LoginRequest loginRequest,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        String username = authService.login(
                loginRequest.username(),
                loginRequest.password(),
                request,
                response
        );

        csrfTokenRepository.saveToken(null, request, response);

        response.setHeader("Cache-Control", "no-store");
        return new AuthResponse(true, username);
    }

    @GetMapping("/csrf")
    public CsrfResponse csrf(
            CsrfToken csrfToken,
            HttpServletResponse response
    ) {
        response.setHeader("Cache-Control", "no-store");
        return new CsrfResponse(
                csrfToken.getHeaderName(),
                csrfToken.getToken()
        );
    }

    @GetMapping("/me")
    public AuthResponse currentUser(
            Authentication authentication,
            HttpServletResponse response
    ) {
        response.setHeader("Cache-Control", "no-store");
        return new AuthResponse(
                true,
                authentication.getName()
        );
    }

    @PostMapping("/logout")
    public MessageResponse logout(
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        authService.logout(request, response);
        response.setHeader("Cache-Control", "no-store");
        return new MessageResponse("退出成功");
    }

    public record LoginRequest(
            @NotBlank(message = "用户名不能为空")
            @Size(
                    max = 50,
                    message = "用户名不能超过50个字符"
            )
            String username,

            @NotBlank(message = "密码不能为空")
            @Size(
                    max = 200,
                    message = "密码长度不正确"
            )
            String password
    ) {
    }

    public record AuthResponse(
            boolean authenticated,
            String username
    ) {
    }

    public record CsrfResponse(
            String headerName,
            String token
    ) {
    }

    public record MessageResponse(String message) {
    }
}
