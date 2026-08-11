package com.nh.customermanager.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

final class DemoWriteRequestFilter extends OncePerRequestFilter {

    private static final PathPatternRequestMatcher DEMO_PATHS =
            PathPatternRequestMatcher.withDefaults()
                    .matcher("/api/demo/**");

    private static final Set<String> WRITE_METHODS = Set.of(
            "POST",
            "PUT",
            "PATCH",
            "DELETE"
    );

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !matchesDemoWriteRequest(request);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
    }

    static boolean matchesDemoWriteRequest(
            HttpServletRequest request
    ) {
        return WRITE_METHODS.contains(request.getMethod())
                && DEMO_PATHS.matches(request);
    }
}
