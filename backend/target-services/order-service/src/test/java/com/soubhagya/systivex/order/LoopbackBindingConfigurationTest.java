package com.soubhagya.systivex.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.net.InetAddress;
import java.net.UnknownHostException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.server.autoconfigure.ServerProperties;
import org.springframework.boot.web.server.autoconfigure.servlet.ServletWebServerFactoryCustomizer;
import org.springframework.boot.web.server.servlet.ConfigurableServletWebServerFactory;
import org.springframework.core.Ordered;

class LoopbackBindingConfigurationTest {

    private final ServerProperties serverProperties = new ServerProperties();
    private final ConfigurableServletWebServerFactory factory =
            mock(ConfigurableServletWebServerFactory.class);
    private final LoopbackBindingConfiguration.LoopbackWebServerFactoryCustomizer customizer =
            new LoopbackBindingConfiguration.LoopbackWebServerFactoryCustomizer(serverProperties);

    @Test
    void defaultsToIpv4LoopbackWhenUnset() {
        customizer.customize(factory);

        verify(factory).setAddress(address("127.0.0.1"));
    }

    @Test
    void acceptsConfiguredIpv4Loopback() {
        serverProperties.setAddress(address("127.0.0.1"));

        customizer.customize(factory);

        verify(factory).setAddress(address("127.0.0.1"));
    }

    @Test
    void acceptsConfiguredIpv6Loopback() {
        InetAddress ipv6Loopback = address("::1");
        serverProperties.setAddress(ipv6Loopback);

        customizer.customize(factory);

        verify(factory).setAddress(ipv6Loopback);
    }

    @Test
    void acceptsConfiguredIpv4LoopbackRange() {
        serverProperties.setAddress(address("127.0.0.2"));

        customizer.customize(factory);

        verify(factory).setAddress(address("127.0.0.2"));
    }

    @Test
    void acceptsConfiguredIpv4MappedIpv6Loopback() {
        InetAddress mappedLoopback = address("::ffff:127.0.0.1");
        serverProperties.setAddress(mappedLoopback);

        customizer.customize(factory);

        verify(factory).setAddress(mappedLoopback);
    }

    @Test
    void rejectsWildcardAddress() {
        serverProperties.setAddress(address("0.0.0.0"));

        assertThatThrownBy(() -> customizer.customize(factory))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("loopback");
        verify(factory, never()).setAddress(any());
    }

    @Test
    void rejectsIpv6WildcardAddress() {
        serverProperties.setAddress(address("::"));

        assertThatThrownBy(() -> customizer.customize(factory))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("loopback");
        verify(factory, never()).setAddress(any());
    }

    @Test
    void rejectsNonLoopbackAddress() {
        serverProperties.setAddress(address("192.0.2.10"));

        assertThatThrownBy(() -> customizer.customize(factory))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("loopback");
        verify(factory, never()).setAddress(any());
    }

    @Test
    void runsAfterFrameworkCustomizer() {
        ServletWebServerFactoryCustomizer framework =
                new ServletWebServerFactoryCustomizer(serverProperties);

        assertThat(customizer.getOrder()).isEqualTo(Ordered.LOWEST_PRECEDENCE);
        assertThat(customizer.getOrder()).isGreaterThan(framework.getOrder());
    }

    private static InetAddress address(String value) {
        try {
            return InetAddress.getByName(value);
        } catch (UnknownHostException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
