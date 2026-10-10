package com.example.finance.config;

import com.example.finance.exception.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Rejects requests whose declared body size exceeds {@code app.max-request-size} before any parsing happens.
 * Every legitimate payload of this API is well under a kilobyte.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RequestSizeLimitFilter extends OncePerRequestFilter {

    private final long maxBytes;
    private final ObjectMapper objectMapper;

    /**
     * @param properties   application properties holding the size limit
     * @param objectMapper JSON mapper for the error body
     */
    public RequestSizeLimitFilter(AppProperties properties, ObjectMapper objectMapper) {
        this.maxBytes = properties.maxRequestSize().toBytes();
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (request.getContentLengthLong() > maxBytes) {
            HttpStatus status = HttpStatus.PAYLOAD_TOO_LARGE;
            response.setStatus(status.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getOutputStream(), ErrorResponse.of(status,
                    "Request body must not exceed " + maxBytes + " bytes", request.getRequestURI()));
            return;
        }
        chain.doFilter(request, response);
    }
}
