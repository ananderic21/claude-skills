package dev.anand.claudeskills.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Owns the {@code requestId} MDC entry for the entire request lifecycle. Because a
 * servlet filter wraps the whole DispatcherServlet dispatch — including exception
 * resolution — the requestId set here is still present when {@code GlobalExceptionHandler}
 * logs, so an error line in error.log can be correlated back to its request.log entry.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    private static final String REQUEST_ID = "requestId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        MDC.put(REQUEST_ID, UUID.randomUUID().toString().substring(0, 8));
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(REQUEST_ID);
        }
    }
}
