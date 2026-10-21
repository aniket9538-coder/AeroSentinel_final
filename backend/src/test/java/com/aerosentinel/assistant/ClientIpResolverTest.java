package com.aerosentinel.assistant;

import com.aerosentinel.assistant.service.ClientIpResolver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.*;

class ClientIpResolverTest {

    @Test
    @DisplayName("Security: Forged X-Forwarded-For is ignored by default (strict direct remoteAddr mode)")
    void testDirectConnection_ForgedXForwardedForIgnored() {
        // Default configuration: no trusted proxies
        ClientIpResolver resolver = new ClientIpResolver("");

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("198.51.100.5");
        request.addHeader("X-Forwarded-For", "203.0.113.195");

        String resolved = resolver.resolveClientIp(request);
        assertEquals("198.51.100.5", resolved,
                "Direct connection must use server-observed remote address and ignore client-supplied X-Forwarded-For");
    }

    @Test
    @DisplayName("Security: Rotating valid-looking X-Forwarded-For IPs does not change identity without trusted proxy")
    void testDirectConnection_RotatingForgedIpsIgnored() {
        ClientIpResolver resolver = new ClientIpResolver("");

        String[] forgedIps = {"1.1.1.1", "8.8.8.8", "203.0.113.1", "192.0.2.100", "2001:db8::1"};
        for (String forgedIp : forgedIps) {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRemoteAddr("198.51.100.5");
            request.addHeader("X-Forwarded-For", forgedIp);

            String resolved = resolver.resolveClientIp(request);
            assertEquals("198.51.100.5", resolved,
                    "Client IP rotation via X-Forwarded-For must be ignored on untrusted connections");
        }
    }

    @Test
    @DisplayName("Security: Arbitrary X-Real-IP and Forwarded headers are ignored")
    void testDirectConnection_ArbitraryForwardingHeadersIgnored() {
        ClientIpResolver resolver = new ClientIpResolver("");

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("198.51.100.5");
        request.addHeader("X-Real-IP", "10.0.0.1");
        request.addHeader("Forwarded", "for=10.0.0.1;proto=https");

        String resolved = resolver.resolveClientIp(request);
        assertEquals("198.51.100.5", resolved);
    }

    @Test
    @DisplayName("Trusted Proxy: Genuine client IP extracted when connection is from configured trusted proxy")
    void testTrustedProxy_ExtractsClientIp() {
        ClientIpResolver resolver = new ClientIpResolver("10.0.0.1");

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.1");
        request.addHeader("X-Forwarded-For", "203.0.113.195");

        String resolved = resolver.resolveClientIp(request);
        assertEquals("203.0.113.195", resolved,
                "Trusted proxy request must extract verified client IP from X-Forwarded-For");
    }

    @Test
    @DisplayName("Trusted Proxy: Multi-hop proxy chain traversed right-to-left, ignoring forged prefixes")
    void testTrustedProxy_MultiHopRightToLeftTraversal() {
        // Two trusted internal reverse proxies: 10.0.0.1 and 10.0.0.2
        ClientIpResolver resolver = new ClientIpResolver("10.0.0.1, 10.0.0.2");

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.2"); // Connected from proxy 2
        // Attacker injected "1.1.1.1", real client was "203.0.113.195", proxy 1 was "10.0.0.1"
        request.addHeader("X-Forwarded-For", "1.1.1.1, 203.0.113.195, 10.0.0.1");

        String resolved = resolver.resolveClientIp(request);
        assertEquals("203.0.113.195", resolved,
                "Right-to-left traversal must pick first untrusted hop from the right, ignoring attacker prefix");
    }

    @Test
    @DisplayName("Trusted Proxy: Malformed or non-IP entries stop traversal and fall back safely")
    void testTrustedProxy_MalformedIpStopsTraversal() {
        ClientIpResolver resolver = new ClientIpResolver("10.0.0.1");

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.1");
        request.addHeader("X-Forwarded-For", "1.1.1.1, invalid_non_ip, 999.999.999.999");

        String resolved = resolver.resolveClientIp(request);
        assertEquals("10.0.0.1", resolved,
                "Malformed IP in proxy chain must trigger safe fallback to direct remote address");
    }

    @Test
    @DisplayName("Trusted Proxy: CIDR notation support for IPv4 and IPv6 subnets")
    void testTrustedProxy_CidrSubnetMatching() {
        ClientIpResolver resolver = new ClientIpResolver("10.0.0.0/8, 172.16.0.0/12, 127.0.0.1/32, ::1/128");

        // 10.254.1.2 is inside 10.0.0.0/8
        MockHttpServletRequest req1 = new MockHttpServletRequest();
        req1.setRemoteAddr("10.254.1.2");
        req1.addHeader("X-Forwarded-For", "198.51.100.42");
        assertEquals("198.51.100.42", resolver.resolveClientIp(req1));

        // 172.31.255.1 is inside 172.16.0.0/12
        MockHttpServletRequest req2 = new MockHttpServletRequest();
        req2.setRemoteAddr("172.31.255.1");
        req2.addHeader("X-Forwarded-For", "198.51.100.43");
        assertEquals("198.51.100.43", resolver.resolveClientIp(req2));

        // 192.168.1.1 is NOT inside trusted subnets -> X-Forwarded-For ignored
        MockHttpServletRequest req3 = new MockHttpServletRequest();
        req3.setRemoteAddr("192.168.1.1");
        req3.addHeader("X-Forwarded-For", "198.51.100.44");
        assertEquals("192.168.1.1", resolver.resolveClientIp(req3));
    }

    @Test
    @DisplayName("Trusted Proxy: Port stripping from IPv4 and IPv6 forwarded entries")
    void testTrustedProxy_PortStripping() {
        ClientIpResolver resolver = new ClientIpResolver("10.0.0.1");

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.1");
        request.addHeader("X-Forwarded-For", "203.0.113.195:8080");

        assertEquals("203.0.113.195", resolver.resolveClientIp(request));

        MockHttpServletRequest reqIpv6 = new MockHttpServletRequest();
        reqIpv6.setRemoteAddr("10.0.0.1");
        reqIpv6.addHeader("X-Forwarded-For", "[2001:db8::1]:443");

        assertEquals("2001:db8::1", resolver.resolveClientIp(reqIpv6));
    }

    @Test
    @DisplayName("Edge Case: Null, empty, or loopback requests handle gracefully")
    void testEdgeCases_NullOrBlank() {
        ClientIpResolver resolver = new ClientIpResolver("");

        assertEquals("unknown-client", resolver.resolveClientIp(null));

        MockHttpServletRequest reqEmpty = new MockHttpServletRequest();
        reqEmpty.setRemoteAddr("");
        assertEquals("unknown-client", resolver.resolveClientIp(reqEmpty));

        MockHttpServletRequest reqLoopback = new MockHttpServletRequest();
        reqLoopback.setRemoteAddr("127.0.0.1");
        assertEquals("127.0.0.1", resolver.resolveClientIp(reqLoopback));
    }
}
