package ru.daniil.shifts.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientIpResolverTest {

    @Test
    void proxyHeadersAreIgnoredUnlessExplicitlyTrusted() {
        MockHttpServletRequest request = request("10.0.0.2");
        request.addHeader("X-Real-IP", "198.51.100.10");
        request.addHeader("X-Forwarded-For", "198.51.100.11, 10.0.0.2");

        ClientIpResolver resolver = new ClientIpResolver(false);
        assertFalse(resolver.trustsProxyHeaders());
        assertEquals("10.0.0.2", resolver.resolve(request));
    }

    @Test
    void trustedEdgePrefersItsOverwrittenRealIpHeader() {
        MockHttpServletRequest request = request("10.0.0.2");
        request.addHeader("X-Real-IP", "198.51.100.10");
        request.addHeader("X-Forwarded-For", "198.51.100.11, 10.0.0.2");

        ClientIpResolver resolver = new ClientIpResolver(true);
        assertTrue(resolver.trustsProxyHeaders());
        assertEquals("198.51.100.10", resolver.resolve(request));
    }

    @Test
    void malformedHeadersFallBackToRemoteAddress() {
        MockHttpServletRequest request = request("203.0.113.8");
        request.addHeader("X-Real-IP", "attacker.example\nforged");
        request.addHeader("X-Forwarded-For", "not-an-ip");

        assertEquals("203.0.113.8", new ClientIpResolver(true).resolve(request));
    }

    @Test
    void ipv6AddressesAreAccepted() {
        MockHttpServletRequest request = request("2001:db8::42");
        assertEquals("2001:db8::42", new ClientIpResolver(false).resolve(request));
    }

    private MockHttpServletRequest request(String remoteAddress) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/");
        request.setRemoteAddr(remoteAddress);
        return request;
    }
}
