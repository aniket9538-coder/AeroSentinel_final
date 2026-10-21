package com.aerosentinel.assistant.service;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Resolves client identity for rate limiting and auditing.
 *
 * SECURITY POLICY:
 * 1. By default, NEVER trust X-Forwarded-For or client-controlled headers.
 * 2. The server-observed remote address (request.getRemoteAddr()) is the default authority.
 * 3. Forwarded headers are ONLY accepted when the direct connection originates from
 *    an explicitly configured trusted proxy (app.assistant.rate-limit.trusted-proxies).
 * 4. When connecting through a trusted proxy, X-Forwarded-For is parsed from right to left
 *    (most immediate proxy hop to least immediate proxy hop), selecting the first untrusted IP.
 * 5. Syntactically valid IP formatting alone does NOT establish trust.
 * 6. Malformed IPs or corrupted forwarding chains cause immediate fallback to the direct remote address.
 */
@Component
public class ClientIpResolver {

    private static final Logger log = LoggerFactory.getLogger(ClientIpResolver.class);

    private static final Pattern IPV4_PATTERN = Pattern.compile(
            "^((25[0-5]|(2[0-4]|1\\d|[1-9]|)\\d)\\.){3}(25[0-5]|(2[0-4]|1\\d|[1-9]|)\\d)$"
    );

    private static final Pattern IPV6_PATTERN = Pattern.compile(
            "^[0-9a-fA-F:]+$"
    );

    private final List<IpMatcher> trustedProxyMatchers = new ArrayList<>();

    public ClientIpResolver(@Value("${app.assistant.rate-limit.trusted-proxies:}") String trustedProxiesConfig) {
        configureTrustedProxies(trustedProxiesConfig);
    }

    private void configureTrustedProxies(String config) {
        trustedProxyMatchers.clear();
        if (config == null || config.isBlank()) {
            log.info("ClientIpResolver: No trusted proxies configured. Strict server-observed remote address mode active.");
            return;
        }

        String[] parts = config.split(",");
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                try {
                    if (trimmed.contains("/")) {
                        trustedProxyMatchers.add(new CidrMatcher(trimmed));
                    } else {
                        trustedProxyMatchers.add(new ExactIpMatcher(trimmed));
                    }
                    log.info("ClientIpResolver: Registered trusted proxy matcher: {}", trimmed);
                } catch (Exception e) {
                    log.error("ClientIpResolver: Failed to parse trusted proxy entry '{}': {}", trimmed, e.getMessage());
                }
            }
        }
    }

    /**
     * Resolves the verified client identifier for an incoming request.
     */
    public String resolveClientIp(HttpServletRequest request) {
        if (request == null) {
            return "unknown-client";
        }

        String remoteAddr = request.getRemoteAddr();
        if (remoteAddr != null) {
            remoteAddr = remoteAddr.trim();
            remoteAddr = stripPortIfPresent(remoteAddr);
        }

        if (remoteAddr == null || remoteAddr.isBlank()) {
            return "unknown-client";
        }

        // Fail-secure: If direct peer is NOT a trusted proxy, NEVER inspect forwarded headers!
        if (!isTrustedProxy(remoteAddr)) {
            return remoteAddr;
        }

        // Direct peer IS a trusted proxy: inspect X-Forwarded-For
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor == null || xForwardedFor.isBlank()) {
            return remoteAddr;
        }

        // Parse right-to-left: the rightmost entry was appended by the trusted proxy closest to us
        String[] hops = xForwardedFor.split(",");
        for (int i = hops.length - 1; i >= 0; i--) {
            String hop = hops[i].trim();
            hop = stripPortIfPresent(hop);

            if (!isValidIpAddress(hop)) {
                log.warn("ClientIpResolver: Malformed IP '{}' in X-Forwarded-For chain from trusted proxy {}. Stopping traversal.", hop, remoteAddr);
                return remoteAddr;
            }

            if (!isTrustedProxy(hop)) {
                // First untrusted IP from the right is the actual client IP observed by our trusted infrastructure
                return hop;
            }
        }

        // If all hops were trusted proxies, return remoteAddr
        return remoteAddr;
    }

    public boolean isTrustedProxy(String ip) {
        if (trustedProxyMatchers.isEmpty() || ip == null || ip.isBlank()) {
            return false;
        }
        for (IpMatcher matcher : trustedProxyMatchers) {
            if (matcher.matches(ip)) {
                return true;
            }
        }
        return false;
    }

    public boolean isValidIpAddress(String ip) {
        if (ip == null || ip.isBlank()) {
            return false;
        }
        if (IPV4_PATTERN.matcher(ip).matches()) {
            return true;
        }
        if (IPV6_PATTERN.matcher(ip).matches() && ip.contains(":")) {
            try {
                InetAddress addr = InetAddress.getByName(ip);
                return addr instanceof java.net.Inet6Address;
            } catch (UnknownHostException e) {
                return false;
            }
        }
        return false;
    }

    private String stripPortIfPresent(String ipWithPort) {
        if (ipWithPort == null) return null;
        String s = ipWithPort.trim();
        // IPv6 bracketed with port: [2001:db8::1]:8080
        if (s.startsWith("[") && s.contains("]")) {
            int closeBracket = s.indexOf(']');
            return s.substring(1, closeBracket);
        }
        // IPv4 with port: 192.0.2.1:8080 (must have exactly one colon)
        int firstColon = s.indexOf(':');
        int lastColon = s.lastIndexOf(':');
        if (firstColon > 0 && firstColon == lastColon) {
            return s.substring(0, firstColon);
        }
        return s;
    }

    public interface IpMatcher {
        boolean matches(String ip);
    }

    public static class ExactIpMatcher implements IpMatcher {
        private final InetAddress address;
        private final String rawIp;

        public ExactIpMatcher(String ip) throws UnknownHostException {
            this.rawIp = ip.trim();
            this.address = InetAddress.getByName(rawIp);
        }

        @Override
        public boolean matches(String ip) {
            if (ip == null) return false;
            if (rawIp.equalsIgnoreCase(ip.trim())) {
                return true;
            }
            try {
                InetAddress target = InetAddress.getByName(ip.trim());
                return Arrays.equals(this.address.getAddress(), target.getAddress());
            } catch (UnknownHostException e) {
                return false;
            }
        }
    }

    public static class CidrMatcher implements IpMatcher {
        private final byte[] networkAddress;
        private final int prefixLength;
        private final boolean isIpv4;

        public CidrMatcher(String cidr) throws UnknownHostException {
            String[] parts = cidr.trim().split("/");
            if (parts.length != 2) {
                throw new IllegalArgumentException("Invalid CIDR format: " + cidr);
            }
            InetAddress netAddr = InetAddress.getByName(parts[0].trim());
            this.networkAddress = netAddr.getAddress();
            this.prefixLength = Integer.parseInt(parts[1].trim());
            this.isIpv4 = (networkAddress.length == 4);

            int maxBits = isIpv4 ? 32 : 128;
            if (prefixLength < 0 || prefixLength > maxBits) {
                throw new IllegalArgumentException("Invalid prefix length " + prefixLength + " for CIDR: " + cidr);
            }
        }

        @Override
        public boolean matches(String ip) {
            try {
                InetAddress target = InetAddress.getByName(ip.trim());
                byte[] targetBytes = target.getAddress();
                if (targetBytes.length != networkAddress.length) {
                    return false;
                }

                int fullBytes = prefixLength / 8;
                int remainingBits = prefixLength % 8;

                for (int i = 0; i < fullBytes; i++) {
                    if (targetBytes[i] != networkAddress[i]) {
                        return false;
                    }
                }

                if (remainingBits > 0) {
                    int mask = 0xFF << (8 - remainingBits);
                    return (targetBytes[fullBytes] & mask) == (networkAddress[fullBytes] & mask);
                }

                return true;
            } catch (UnknownHostException e) {
                return false;
            }
        }
    }
}
