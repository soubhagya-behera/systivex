package com.soubhagya.systivex.management.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.net.InetAddress;
import java.net.UnknownHostException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.autoconfigure.web.ManagementContextConfiguration;
import org.springframework.boot.actuate.autoconfigure.web.server.ManagementServerProperties;
import org.springframework.boot.context.annotation.ImportCandidates;
import org.springframework.boot.web.server.servlet.ConfigurableServletWebServerFactory;
import org.springframework.core.Ordered;

class OrderManagementLoopbackConfigurationTest {

    private final ManagementServerProperties managementServerProperties =
            new ManagementServerProperties();
    private final ConfigurableServletWebServerFactory factory =
            mock(ConfigurableServletWebServerFactory.class);
    private final OrderManagementLoopbackConfiguration
                    .ManagementLoopbackWebServerFactoryCustomizer customizer =
            new OrderManagementLoopbackConfiguration
                    .ManagementLoopbackWebServerFactoryCustomizer(managementServerProperties);

    @Test
    void defaultsToIpv4LoopbackWhenUnset() {
        customizer.customize(factory);

        verify(factory).setAddress(address("127.0.0.1"));
    }

    @Test
    void acceptsConfiguredIpv4Loopback() {
        managementServerProperties.setAddress(address("127.0.0.1"));

        customizer.customize(factory);

        verify(factory).setAddress(address("127.0.0.1"));
    }

    @Test
    void acceptsConfiguredIpv4LoopbackRange() {
        managementServerProperties.setAddress(address("127.0.0.2"));

        customizer.customize(factory);

        verify(factory).setAddress(address("127.0.0.2"));
    }

    @Test
    void acceptsConfiguredIpv6Loopback() {
        InetAddress ipv6Loopback = address("::1");
        managementServerProperties.setAddress(ipv6Loopback);

        customizer.customize(factory);

        verify(factory).setAddress(ipv6Loopback);
    }

    @Test
    void acceptsConfiguredIpv4MappedIpv6Loopback() {
        InetAddress mappedLoopback = address("::ffff:127.0.0.1");
        managementServerProperties.setAddress(mappedLoopback);

        customizer.customize(factory);

        verify(factory).setAddress(mappedLoopback);
    }

    @Test
    void rejectsIpv4WildcardAddress() {
        managementServerProperties.setAddress(address("0.0.0.0"));

        assertThatThrownBy(() -> customizer.customize(factory))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("management.server.address")
                .hasMessageContaining("loopback");
        verify(factory, never()).setAddress(any());
    }

    @Test
    void rejectsIpv6WildcardAddress() {
        managementServerProperties.setAddress(address("::"));

        assertThatThrownBy(() -> customizer.customize(factory))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("management.server.address")
                .hasMessageContaining("loopback");
        verify(factory, never()).setAddress(any());
    }

    @Test
    void rejectsNonLoopbackAddress() {
        managementServerProperties.setAddress(address("192.0.2.10"));

        assertThatThrownBy(() -> customizer.customize(factory))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("management.server.address")
                .hasMessageContaining("loopback");
        verify(factory, never()).setAddress(any());
    }

    @Test
    void runsAfterFrameworkCustomizer() {
        assertThat(customizer.getOrder()).isEqualTo(Ordered.LOWEST_PRECEDENCE);
    }

    @Test
    void isRegisteredInChildManagementContext() {
        ImportCandidates candidates =
                ImportCandidates.load(
                        ManagementContextConfiguration.class,
                        OrderManagementLoopbackConfigurationTest.class.getClassLoader());

        assertThat(candidates.getCandidates())
                .contains(OrderManagementLoopbackConfiguration.class.getName());
    }

    private static InetAddress address(String value) {
        try {
            return InetAddress.getByName(value);
        } catch (UnknownHostException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
