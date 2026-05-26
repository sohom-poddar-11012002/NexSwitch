package com.nexswitch.adapters.inbound.rest;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class MdcRequestFilterTest {

    private final MdcRequestFilter filter = new MdcRequestFilter();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void propagatesIncomingTraceId() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader(MdcRequestFilter.TRACE_ID_HEADER, "abc-123");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(req, res, chain);

        verify(chain).doFilter(req, res);
        assertThat(res.getHeader(MdcRequestFilter.TRACE_ID_HEADER)).isEqualTo("abc-123");
    }

    @Test
    void generatesTraceIdWhenAbsent() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(req, res, chain);

        assertThat(res.getHeader(MdcRequestFilter.TRACE_ID_HEADER)).isNotBlank();
    }

    @Test
    void clearsMdcAfterChain() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader(MdcRequestFilter.TRACE_ID_HEADER, "trace-xyz");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(req, res, chain);

        assertThat(MDC.get("traceId")).isNull();
        assertThat(MDC.get("requestId")).isNull();
    }

    @Test
    void clearsMdcEvenWhenChainThrows() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        doThrow(new RuntimeException("chain failure")).when(chain).doFilter(req, res);

        try {
            filter.doFilterInternal(req, res, chain);
        } catch (RuntimeException ignored) {}

        assertThat(MDC.get("traceId")).isNull();
    }
}
