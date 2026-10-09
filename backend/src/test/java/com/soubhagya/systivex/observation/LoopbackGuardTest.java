package com.soubhagya.systivex.observation;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

/** The loopback boundary trusts the transport peer only — never headers. */
class LoopbackGuardTest {

    private final LoopbackGuard guard = new LoopbackGuard();

    @Test
    void loopbackPeersAreAllowed() {
        for (String peer : new String[] {"127.0.0.1", "::1", "0:0:0:0:0:0:0:1"}) {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRemoteAddr(peer);
            assertThatNoException().isThrownBy(() -> guard.check(request));
        }
    }

    @Test
    void nonLoopbackPeerIsDeniedEvenWithSpoofedHeaders() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.9");
        request.addHeader("X-Forwarded-For", "127.0.0.1");
        assertThatThrownBy(() -> guard.check(request)).isInstanceOf(SyncForbiddenException.class);
    }

    @Test
    void missingPeerIsDenied() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(null);
        assertThatThrownBy(() -> guard.check(request)).isInstanceOf(SyncForbiddenException.class);
    }
}
