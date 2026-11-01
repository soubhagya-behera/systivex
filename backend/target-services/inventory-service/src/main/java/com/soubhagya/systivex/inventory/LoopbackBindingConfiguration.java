package com.soubhagya.systivex.inventory;

import java.net.InetAddress;
import java.net.UnknownHostException;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.server.autoconfigure.ServerProperties;
import org.springframework.boot.web.server.servlet.ConfigurableServletWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration(proxyBeanMethods = false)
public class LoopbackBindingConfiguration {

    @Bean
    LoopbackWebServerFactoryCustomizer loopbackWebServerFactoryCustomizer(
            ServerProperties serverProperties) {
        return new LoopbackWebServerFactoryCustomizer(serverProperties);
    }

    static final class LoopbackWebServerFactoryCustomizer
            implements WebServerFactoryCustomizer<ConfigurableServletWebServerFactory>, Ordered {

        private static final InetAddress IPV4_LOOPBACK = ipv4Loopback();

        private final ServerProperties serverProperties;

        LoopbackWebServerFactoryCustomizer(ServerProperties serverProperties) {
            this.serverProperties = serverProperties;
        }

        @Override
        public int getOrder() {
            return Ordered.LOWEST_PRECEDENCE;
        }

        @Override
        public void customize(ConfigurableServletWebServerFactory factory) {
            factory.setAddress(resolve(serverProperties.getAddress()));
        }

        static InetAddress resolve(InetAddress configured) {
            if (configured == null) {
                return IPV4_LOOPBACK;
            }
            if (!configured.isLoopbackAddress()) {
                throw new IllegalStateException(
                        "server.address must be a loopback address; refusing to bind "
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
