package com.soubhagya.systivex.localmodel;

import java.net.URI;
import java.util.Locale;
import java.util.Set;

public final class OllamaEndpointValidator {

    private static final Set<String> LOOPBACK_HOSTS =
            Set.of("127.0.0.1", "::1", "0:0:0:0:0:0:0:1");

    private OllamaEndpointValidator() {
    }

    public static URI validate(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException("systivex.local.base-url is required when the local provider is enabled");
        }
        URI uri;
        try {
            uri = new URI(baseUrl.strip());
        } catch (Exception ex) {
            throw new IllegalStateException("systivex.local.base-url is malformed", ex);
        }
        if (!"http".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalStateException(
                    "systivex.local.base-url must use the http scheme only: " + baseUrl);
        }
        if (uri.getUserInfo() != null) {
            throw new IllegalStateException("systivex.local.base-url must not contain userinfo");
        }
        String host = uri.getHost();
        if (host != null && (host.startsWith("[") && host.endsWith("]"))) {
            host = host.substring(1, host.length() - 1);
        }
        if (host == null || !LOOPBACK_HOSTS.contains(host.strip().toLowerCase(Locale.ROOT))) {
            throw new IllegalStateException(
                    "systivex.local.base-url host must be a loopback IP literal "
                            + "(127.0.0.1, ::1, 0:0:0:0:0:0:1); hostnames and DNS are rejected: " + baseUrl);
        }
        if (uri.getPort() <= 0) {
            throw new IllegalStateException("systivex.local.base-url must carry an explicit port: " + baseUrl);
        }
        String path = uri.getPath();
        if (path != null && !path.isBlank() && !"/".equals(path)) {
            throw new IllegalStateException("systivex.local.base-url must not contain a path: " + baseUrl);
        }
        if (uri.getQuery() != null || uri.getFragment() != null) {
            throw new IllegalStateException("systivex.local.base-url must not contain a query or fragment");
        }
        return uri;
    }
}
