package com.soubhagya.systivex.observation;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Phase 4 localhost-only boundary for the mutating sync endpoint. This
 * phase introduces no authentication system, so the endpoint instead
 * refuses every caller whose transport peer is not the loopback interface,
 * and the server itself binds to loopback by default (see {@code
 * server.address} in {@code application.example.properties}).
 *
 * <p>The check reads only {@link HttpServletRequest#getRemoteAddr} — the
 * container-reported TCP peer. {@code X-Forwarded-For} and friends are
 * deliberately ignored: they are client-controlled and must never act as
 * access control. Fail-closed: a missing peer address is denied.
 */
@Component
public class LoopbackGuard {

    private static final Set<String> LOOPBACK =
            Set.of("127.0.0.1", "::1", "0:0:0:0:0:0:0:1");

    public void check(HttpServletRequest request) {
        String peer = request == null ? null : request.getRemoteAddr();
        if (peer == null || !LOOPBACK.contains(peer.strip().toLowerCase(Locale.ROOT))) {
            throw new SyncForbiddenException(
                    "Twin synchronization is available on the loopback interface only");
        }
    }
}
