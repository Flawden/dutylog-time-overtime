package ru.daniil.shifts.config;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecurityEventLoggerTest {
    private Logger auditLogger;
    private ListAppender<ILoggingEvent> appender;
    private SecurityEventLogger events;

    @BeforeEach
    void setUp() {
        auditLogger = (Logger) LoggerFactory.getLogger("SECURITY_AUDIT");
        appender = new ListAppender<>();
        appender.start();
        auditLogger.addAppender(appender);
        events = new SecurityEventLogger(new ClientIpResolver(true));
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
        auditLogger.detachAppender(appender);
        appender.stop();
    }

    @Test
    void explicitRequestProducesStructuredInfoEventWithFirstForwardedIp() {
        MockHttpServletRequest request = request("POST", "/api/v1/mobile/auth/login");
        request.setAttribute(RequestDiagnosticsFilter.REQUEST_ID_ATTRIBUTE, "req-audit-1");
        request.addHeader("X-Forwarded-For", "198.51.100.7, 10.0.0.3");

        events.info(request, "AUTH_LOGIN_SUCCEEDED", "alex", "accepted", "channel=mobile");

        ILoggingEvent event = singleEvent();
        assertEquals(Level.INFO, event.getLevel());
        String line = event.getFormattedMessage();
        assertTrue(line.contains("securityEvent=AUTH_LOGIN_SUCCEEDED"));
        assertTrue(line.contains("result=accepted"));
        assertTrue(line.contains("username=alex"));
        assertTrue(line.contains("ip=198.51.100.7"));
        assertTrue(line.contains("method=POST"));
        assertTrue(line.contains("path=/api/v1/mobile/auth/login"));
        assertTrue(line.contains("requestId=req-audit-1"));
        assertTrue(line.contains("detail=channel=mobile"));
    }

    @Test
    void warningUsesRealIpWhenForwardedHeaderIsMissing() {
        MockHttpServletRequest request = request("GET", "/api/admin/status");
        request.addHeader("X-Real-IP", " 203.0.113.9 ");

        events.warn(request, "AUTH_ACCESS_DENIED", null, "rejected", "channel=web");

        ILoggingEvent event = singleEvent();
        assertEquals(Level.WARN, event.getLevel());
        assertTrue(event.getFormattedMessage().contains("ip=203.0.113.9"));
        assertTrue(event.getFormattedMessage().contains("username=-"));
    }

    @Test
    void currentRequestOverloadUsesRequestContextAndRemoteAddressFallback() {
        MockHttpServletRequest request = request("DELETE", "/logout");
        request.setRemoteAddr("192.0.2.44");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        events.info("AUTH_LOGOUT", "user", "accepted", "source=web");

        String line = singleEvent().getFormattedMessage();
        assertTrue(line.contains("ip=192.0.2.44"));
        assertTrue(line.contains("method=DELETE"));
        assertTrue(line.contains("path=/logout"));
    }

    @Test
    void controlCharactersAreFlattenedAndEveryValueIsBounded() {
        MockHttpServletRequest request = request("GET", "/api/test");
        String detail = "line1\r\nline2\t" + "x".repeat(250);

        events.warn(request, "EVENT\nINJECT", " user\rname ", " rejected\t", detail);

        String line = singleEvent().getFormattedMessage();
        assertFalse(line.contains("\r"));
        assertFalse(line.contains("\n"));
        assertFalse(line.contains("\t"));
        assertTrue(line.contains("securityEvent=EVENT_INJECT"));
        assertTrue(line.contains("username=user_name"));
        String loggedDetail = line.substring(line.indexOf("detail=") + "detail=".length());
        assertEquals(160, loggedDetail.length());
    }

    @Test
    void missingRequestAndBlankValuesUseStableDashPlaceholders() {
        events.info(" ", null, "", null);

        String line = singleEvent().getFormattedMessage();
        assertTrue(line.contains("securityEvent=-"));
        assertTrue(line.contains("result=-"));
        assertTrue(line.contains("username=-"));
        assertTrue(line.contains("ip=-"));
        assertTrue(line.contains("method=-"));
        assertTrue(line.contains("path=-"));
        assertTrue(line.contains("requestId=-"));
        assertTrue(line.endsWith("detail=-"));
    }

    private MockHttpServletRequest request(String method, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setRemoteAddr("127.0.0.1");
        return request;
    }

    private ILoggingEvent singleEvent() {
        assertEquals(1, appender.list.size());
        return appender.list.get(0);
    }
}
