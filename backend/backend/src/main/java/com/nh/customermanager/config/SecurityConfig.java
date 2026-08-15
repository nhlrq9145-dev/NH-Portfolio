package com.nh.customermanager.config;

import com.nh.customermanager.entity.AdminUser;
import com.nh.customermanager.repository.AdminUserRepository;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandlerImpl;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.security.web.firewall.HttpStatusRequestRejectedHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

@Configuration
public class SecurityConfig {

    private static final String INVALID_CORS_ORIGIN_MESSAGE =
            "Invalid app.cors.allowed-origin: expected one exact HTTP(S) origin";

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public UserDetailsService userDetailsService(
            AdminUserRepository adminUserRepository
    ) {
        return username -> {
            String normalizedUsername = username == null
                    ? ""
                    : username.trim();

            AdminUser adminUser = adminUserRepository
                    .findByUsernameIgnoreCase(normalizedUsername)
                    .orElseThrow(() ->
                            new UsernameNotFoundException(
                                    "用户名或密码错误"
                            )
                    );

            return User.withUsername(adminUser.getUsername())
                    .password(adminUser.getPasswordHash())
                    .roles("ADMIN")
                    .disabled(!adminUser.isEnabled())
                    .build();
        };
    }

    @Bean
    public AuthenticationProvider authenticationProvider(
            UserDetailsService userDetailsService,
            PasswordEncoder passwordEncoder
    ) {
        DaoAuthenticationProvider provider =
                new DaoAuthenticationProvider(userDetailsService);

        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationProvider authenticationProvider
    ) {
        return new ProviderManager(
                List.of(authenticationProvider)
        );
    }

    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    public CsrfTokenRepository csrfTokenRepository() {
        return new HttpSessionCsrfTokenRepository();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origin}")
            String allowedOrigin
    ) {
        String validatedOrigin = validateAllowedOrigin(allowedOrigin);
        CorsConfiguration configuration =
                new CorsConfiguration();

        configuration.setAllowedOrigins(
                List.of(validatedOrigin)
        );

        configuration.setAllowedMethods(
                List.of(
                        "GET",
                        "HEAD",
                        "POST",
                        "PUT",
                        "DELETE",
                        "OPTIONS"
                )
        );

        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source =
                new UrlBasedCorsConfigurationSource();

        source.registerCorsConfiguration(
                "/**",
                configuration
        );

        return source;
    }

    private static String validateAllowedOrigin(String configuredOrigin) {
        if (configuredOrigin == null) {
            throw invalidCorsOrigin();
        }

        String origin = configuredOrigin.trim();
        if (origin.isEmpty()
                || origin.contains(",")
                || origin.contains("*")) {
            throw invalidCorsOrigin();
        }

        try {
            URI uri = new URI(origin).parseServerAuthority();
            String scheme = uri.getScheme();
            String host = uri.getHost();
            String authority = uri.getRawAuthority();
            int port = uri.getPort();

            if ((!"http".equalsIgnoreCase(scheme)
                    && !"https".equalsIgnoreCase(scheme))
                    || host == null
                    || host.isBlank()
                    || authority == null
                    || authority.endsWith(":")
                    || uri.getRawUserInfo() != null
                    || (uri.getRawPath() != null
                        && !uri.getRawPath().isEmpty())
                    || uri.getRawQuery() != null
                    || uri.getRawFragment() != null
                    || (port != -1 && (port < 1 || port > 65535))) {
                throw invalidCorsOrigin();
            }

            String normalizedScheme = scheme.toLowerCase(Locale.ROOT);
            String normalizedHost = host.toLowerCase(Locale.ROOT);
            int normalizedPort =
                    ("http".equals(normalizedScheme) && port == 80)
                            || ("https".equals(normalizedScheme)
                            && port == 443)
                            ? -1
                            : port;

            return new URI(
                    normalizedScheme,
                    null,
                    normalizedHost,
                    normalizedPort,
                    null,
                    null,
                    null
            ).toASCIIString();
        } catch (URISyntaxException exception) {
            throw invalidCorsOrigin();
        }
    }

    private static IllegalStateException invalidCorsOrigin() {
        return new IllegalStateException(INVALID_CORS_ORIGIN_MESSAGE);
    }

    @Bean
    public WebSecurityCustomizer webSecurityCustomizer() {
        HttpStatusRequestRejectedHandler badRequestHandler =
                new HttpStatusRequestRejectedHandler();
        HttpStatusRequestRejectedHandler demoWriteHandler =
                new HttpStatusRequestRejectedHandler(
                        HttpServletResponse.SC_FORBIDDEN
                );

        return web -> web.requestRejectedHandler(
                (request, response, exception) -> {
                    if (DemoWriteRequestFilter
                            .matchesDemoWriteRequest(request)) {
                        demoWriteHandler.handle(
                                request,
                                response,
                                exception
                        );
                        return;
                    }

                    badRequestHandler.handle(
                            request,
                            response,
                            exception
                    );
                }
        );
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            CorsConfigurationSource corsConfigurationSource,
            SecurityContextRepository securityContextRepository,
            CsrfTokenRepository csrfTokenRepository
    ) throws Exception {
        AccessDeniedHandlerImpl defaultAccessDeniedHandler =
                new AccessDeniedHandlerImpl();

        http
                .cors(cors ->
                        cors.configurationSource(
                                corsConfigurationSource
                        )
                )
                .csrf(csrf -> csrf.csrfTokenRepository(
                        csrfTokenRepository
                ))
                .securityContext(context ->
                        context
                                .securityContextRepository(
                                        securityContextRepository
                                )
                                .requireExplicitSave(true)
                )
                .sessionManagement(session ->
                        session.sessionCreationPolicy(
                                SessionCreationPolicy.IF_REQUIRED
                        )
                )
                .authorizeHttpRequests(authorize ->
                        authorize
                                .requestMatchers(
                                        HttpMethod.OPTIONS,
                                        "/**"
                                ).permitAll()
                                .requestMatchers(
                                        HttpMethod.GET,
                                        "/api/demo/customers"
                                ).permitAll()
                                .requestMatchers(
                                        HttpMethod.HEAD,
                                        "/api/demo/customers"
                                ).permitAll()
                                .requestMatchers(
                                        HttpMethod.GET,
                                        "/api/auth/csrf"
                                ).permitAll()
                                .requestMatchers(
                                        "/api/demo/**"
                                ).denyAll()
                                .requestMatchers(
                                        "/api/ping",
                                        "/api/auth/login",
                                        "/error"
                                ).permitAll()
                                .requestMatchers(
                                        "/api/auth/me",
                                        "/api/auth/logout",
                                        "/api/customers/**"
                                ).authenticated()
                                .anyRequest().permitAll()
                )
                .addFilterBefore(
                        new DemoWriteRequestFilter(),
                        CsrfFilter.class
                )
                .exceptionHandling(exceptions ->
                        exceptions
                                .authenticationEntryPoint(
                                (request, response, exception) -> {
                                    response.setStatus(401);
                                    response.setContentType(
                                            MediaType.APPLICATION_JSON_VALUE
                                    );
                                    response.setCharacterEncoding(
                                            StandardCharsets.UTF_8.name()
                                    );
                                    response.getWriter().write(
                                            """
                                            {"status":401,"message":"请先登录"}
                                            """
                                    );
                                }
                        )
                                .accessDeniedHandler(
                                        (request, response, exception) -> {
                                            if (!(exception
                                                    instanceof CsrfException)) {
                                                defaultAccessDeniedHandler
                                                        .handle(
                                                                request,
                                                                response,
                                                                exception
                                                        );
                                                return;
                                            }

                                            response.setStatus(403);
                                            response.setContentType(
                                                    MediaType
                                                            .APPLICATION_JSON_VALUE
                                            );
                                            response.setCharacterEncoding(
                                                    StandardCharsets.UTF_8.name()
                                            );
                                            response.getWriter().write(
                                                    """
                                                    {"status":403,"message":"请求安全校验失败，请刷新页面后重试"}
                                                    """
                                            );
                                        }
                                )
                )
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout.disable());

        return http.build();
    }
}
