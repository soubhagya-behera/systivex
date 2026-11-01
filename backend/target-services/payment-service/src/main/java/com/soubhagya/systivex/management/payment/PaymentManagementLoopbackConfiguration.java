package com.soubhagya.systivex.management.payment;

import java.net.InetAddress;
import java.net.UnknownHostException;
import org.springframework.boot.actuate.autoconfigure.web.ManagementContextConfiguration;
import org.springframework.boot.actuate.autoconfigure.web.ManagementContextType;
import org.springframework.boot.actuate.autoconfigure.web.server.ManagementServerProperties;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.server.servlet.ConfigurableServletWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

@ManagementContextConfiguration(value = ManagementContextType.CHILD, proxyBeanMethods = false)
public class PaymentManagementLoopbackConfiguration {

    @Bean
    ManagementLoopbackWebServerFactoryCustomizer managementLoopbackWebServerFactoryCustomizer(
            ManagementServerProperties managementServerProperties) {
        return new ManagementLoopbackWebServerFactoryCustomizer(managementServerProperties);
    }

    static final class ManagementLoopbackWebServerFactoryCustomizer
            implements WebServerFactoryCustomizer<ConfigurableServletWebServerFactory>, Ordered {

        private static final InetAddress IPV4_LOOPBACK = ipv4Loopback();

        private final ManagementServerProperties managementServerProperties;

        ManagementLoopbackWebServerFactoryCustomizer(
                ManagementServerProperties managementServerProperties) {
            this.managementServerProperties = managementServerProperties;
        }

        @Override
        public int getOrder() {
            return Ordered.LOWEST_PRECEDENCE;
        }

        @Override
        public void customize(ConfigurableServletWebServerFactory factory) {
            factory.setAddress(resolve(managementServerProperties.getAddress()));
        }

        static InetAddress resolve(InetAddress configured) {
            if (configured == null) {
                return IPV4_LOOPBACK;
            }
            if (!configured.isLoopbackAddress()) {
                throw new IllegalStateException(
                        "management.server.address must be a loopback address; refusing to bind "
                                + configured.getHostAddress());
            }
            return configured;
        }

        private static InetAddress ipv4Loopback() {
            try {
                return InetAddress.getByName("127.0.0.1");
            } catch (UnknownHostException ex) {
                throw new IllegalStateException("IPv4 loopback address is unavailable", ex);
            }
        }
    }
}
