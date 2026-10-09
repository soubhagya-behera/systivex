package com.soubhagya.systivex.inventory;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

/**
 * Hands the Boot-managed {@link OpenTelemetry} instance to the OTEL Logback
 * appender declared in {@code logback-spring.xml}.
 *
 * <p>Why this exists: Boot auto-configures the OTel SDK and the OTLP log
 * exporter, but installs no logging bridge — the appender buffers records
 * until {@code install} is called, and without this bean nothing is ever
 * emitted. Console logging is untouched; this only activates the second
 * sink (collector → Loki). Re-running it (tests, restarts) is idempotent.
 */
@Component
public class OtelLogAppenderInstaller implements ApplicationListener<ApplicationReadyEvent> {

    private final OpenTelemetry openTelemetry;

    public OtelLogAppenderInstaller(OpenTelemetry openTelemetry) {
        this.openTelemetry = openTelemetry;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        OpenTelemetryAppender.install(openTelemetry);
    }
}
