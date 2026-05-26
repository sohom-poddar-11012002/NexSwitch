package com.nexswitch.adapters.inbound.rest;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

// LEARN: OncePerRequestFilter guarantees a single execution per request even in async dispatch —
//        unlike a raw Filter which can fire again on ASYNC_DISPATCH. Seeds MDC so every log line
//        downstream carries traceId without callers needing to pass it explicitly.
@Component
@Order(1)
public class MdcRequestFilter extends OncePerRequestFilter {

    static final String TRACE_ID_HEADER = "X-Trace-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        try {
            String traceId = request.getHeader(TRACE_ID_HEADER);
            if (traceId == null || traceId.isBlank()) {
                traceId = UUID.randomUUID().toString();
            }
            MDC.put("traceId",   traceId);
            MDC.put("requestId", UUID.randomUUID().toString());
            // LEARN: traceId echoed back so callers can correlate client logs with server logs
            //        without a distributed tracing agent. Cheap substitute for Zipkin in early dev.
            response.setHeader(TRACE_ID_HEADER, traceId);
            chain.doFilter(request, response);
        } finally {
            // LEARN: MDC.clear() must be in finally — any exception before chain.doFilter would
            //        otherwise leave stale traceId in a pooled thread, poisoning the next request.
            MDC.clear();
        }
    }
}
