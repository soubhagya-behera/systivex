package com.soubhagya.systivex.observation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.soubhagya.systivex.twin.model.SystemEntityType;
import com.soubhagya.systivex.twin.model.SystemRelationshipType;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit tests for the read-only repository scanner. Fixture repositories are
 * built in a temp directory (never the real tree); one test scans the real
 * {@code target-services} tree read-only to pin the expected production
 * graph.
 */
class TargetRepositoryScannerTest {

    private final TargetRepositoryScanner scanner = new TargetRepositoryScanner();

    @Test
    void scanFixtureRepoProducesExpectedGraph(@TempDir Path root) throws IOException {
        writeService(
                root,
                "gateway-service",
                """
                spring.application.name=gateway-service
                server.port=8081
                app.order-service.url=${ORDER_SERVICE_URL:http://localhost:8082}
                """,
                """
                package com.example.gateway.api;
                import org.springframework.web.bind.annotation.PostMapping;
                import org.springframework.web.bind.annotation.RequestMapping;
                import org.springframework.web.bind.annotation.RestController;
                @RestController
                @RequestMapping("/api/v1/checkout")
                public class CheckoutController {
                    @PostMapping
                    public String checkout() { return "ok"; }
                }
                """,
                null);
        writeService(
                root,
                "order-service",
                """
                spring.application.name=order-service
                server.port=8082
                app.inventory-service.url=${INVENTORY_SERVICE_URL:http://localhost:8084}
                app.payment-service.url=${PAYMENT_SERVICE_URL:http://localhost:8083}
                spring.datasource.url=jdbc:postgresql://localhost:5432/order_db
                spring.datasource.username=order_app
                """,
                """
                package com.example.order.api;
                import org.springframework.web.bind.annotation.PostMapping;
                import org.springframework.web.bind.annotation.RequestMapping;
                import org.springframework.web.bind.annotation.RestController;
                @RestController
                @RequestMapping("/api/v1/orders")
                public class OrderController {
                    @PostMapping
                    public String checkout() { return "ok"; }
                }
                """,
                "CREATE TABLE orders (id UUID NOT NULL PRIMARY KEY);");
        writeService(
                root,
                "inventory-service",
                """
                spring.application.name=inventory-service
                server.port=8084
                spring.datasource.url=jdbc:postgresql://localhost:5432/inventory_db
                spring.datasource.username=inventory_app
                """,
                """
                package com.example.inventory.api;
                import org.springframework.web.bind.annotation.PostMapping;
                import org.springframework.web.bind.annotation.RequestMapping;
                import org.springframework.web.bind.annotation.RestController;
                @RestController
                @RequestMapping("/internal/v1/inventory")
                public class InventoryController {
                    @PostMapping("/reserve")
                    public String reserve() { return "ok"; }
                }
                """,
                """
                CREATE TABLE inventory_items (product_id VARCHAR(64) NOT NULL PRIMARY KEY);
                CREATE TABLE inventory_reservations (id UUID NOT NULL PRIMARY KEY);
                """);
        writeService(
                root,
                "payment-service",
                """
                spring.application.name=payment-service
                server.port=8083
                spring.datasource.url=jdbc:postgresql://localhost:5432/payment_db
                spring.datasource.username=payment_app
                """,
                """
                package com.example.payment.api;
                import org.springframework.web.bind.annotation.PostMapping;
                import org.springframework.web.bind.annotation.RequestMapping;
                import org.springframework.web.bind.annotation.RestController;
                @RestController
                @RequestMapping("/internal/v1/payments")
                public class PaymentController {
                    @PostMapping("/authorize")
                    public String authorize() { return "ok"; }
                }
                """,
                "CREATE TABLE payments (id UUID NOT NULL PRIMARY KEY);");

        DiscoveryResult result = scanner.scan(root);

        Map<String, DiscoveredEntity> byRef =
                result.entities().stream()
                        .collect(Collectors.toMap(DiscoveredEntity::externalRef, e -> e));
        assertThat(byRef.keySet())
                .containsExactlyInAnyOrder(
                        "target-service:gateway-service",
                        "target-service:order-service",
                        "target-service:inventory-service",
                        "target-service:payment-service",
                        "target-database:order_db",
                        "target-database:payment_db",
                        "target-database:inventory_db",
                        "target-table:order_db.orders",
                        "target-table:payment_db.payments",
                        "target-table:inventory_db.inventory_items",
                        "target-table:inventory_db.inventory_reservations",
                        "target-api:gateway-service:POST /api/v1/checkout",
                        "target-api:order-service:POST /api/v1/orders",
                        "target-api:inventory-service:POST /internal/v1/inventory/reserve",
                        "target-api:payment-service:POST /internal/v1/payments/authorize");
        assertThat(result.entities()).hasSize(15);

        DiscoveredEntity order = byRef.get("target-service:order-service");
        assertThat(order.type()).isEqualTo(SystemEntityType.SERVICE);
        assertThat(order.environment()).isEqualTo("dev");
        assertThat(order.metadata())
                .containsEntry("managedBy", "repository-connector")
                .containsEntry("connectorVersion", "phase4-v1")
                .containsEntry("port", 8082)
                .containsEntry("database", "order_db");
        DiscoveredEntity orders = byRef.get("target-table:order_db.orders");
        assertThat(orders.type()).isEqualTo(SystemEntityType.TABLE);

        List<String> edges =
                result.relationships().stream()
                        .map(r -> r.sourceRef() + " " + r.type() + " " + r.targetRef())
                        .sorted()
                        .toList();
        assertThat(edges)
                .containsExactlyInAnyOrder(
                        "target-service:gateway-service CALLS target-service:order-service",
                        "target-service:order-service CALLS target-service:inventory-service",
                        "target-service:order-service CALLS target-service:payment-service",
                        "target-service:gateway-service EXPOSES"
                                + " target-api:gateway-service:POST /api/v1/checkout",
                        "target-service:order-service EXPOSES"
                                + " target-api:order-service:POST /api/v1/orders",
                        "target-service:inventory-service EXPOSES"
                                + " target-api:inventory-service:POST /internal/v1/inventory/reserve",
                        "target-service:payment-service EXPOSES"
                                + " target-api:payment-service:POST /internal/v1/payments/authorize",
                        "target-service:order-service DEPENDS_ON target-database:order_db",
                        "target-service:payment-service DEPENDS_ON target-database:payment_db",
                        "target-service:inventory-service DEPENDS_ON target-database:inventory_db",
                        "target-database:order_db CONTAINS target-table:order_db.orders",
                        "target-database:payment_db CONTAINS target-table:payment_db.payments",
                        "target-database:inventory_db CONTAINS target-table:inventory_db.inventory_items",
                        "target-database:inventory_db CONTAINS"
                                + " target-table:inventory_db.inventory_reservations",
                        "target-service:order-service WRITES target-table:order_db.orders",
                        "target-service:payment-service WRITES target-table:payment_db.payments",
                        "target-service:inventory-service WRITES"
                                + " target-table:inventory_db.inventory_items",
                        "target-service:inventory-service WRITES"
                                + " target-table:inventory_db.inventory_reservations");
        assertThat(result.relationships()).hasSize(18);
    }

    @Test
    void scanRealTargetServicesProducesKnownGraph() {
        Path real = Path.of("").toAbsolutePath().resolve("target-services");
        org.junit.jupiter.api.Assumptions.assumeTrue(
                Files.isDirectory(real), "real target-services tree not present");
        DiscoveryResult result = scanner.scan(real);

        assertThat(result.entities()).hasSize(15);
        assertThat(result.relationships()).hasSize(18);
        List<String> callEdges =
                result.relationships().stream()
                        .filter(r -> r.type() == SystemRelationshipType.CALLS)
                        .map(r -> r.sourceRef() + "->" + r.targetRef())
                        .sorted()
                        .toList();
        assertThat(callEdges)
                .containsExactly(
                        "target-service:gateway-service->target-service:order-service",
                        "target-service:order-service->target-service:inventory-service",
                        "target-service:order-service->target-service:payment-service");
    }

    @Test
    void missingRootFailsClosed(@TempDir Path root) {
        assertThatThrownBy(() -> scanner.scan(root.resolve("does-not-exist")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void emptyRootFailsClosed(@TempDir Path root) {
        assertThatThrownBy(() -> scanner.scan(root)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void unknownDownstreamPortFailsClosed(@TempDir Path root) throws IOException {
        writeService(
                root,
                "gateway-service",
                """
                spring.application.name=gateway-service
                server.port=8081
                app.order-service.url=http://localhost:8999
                """,
                """
                @RequestMapping("/api/v1/checkout")
                public class CheckoutController {
                    @PostMapping
                    public String checkout() { return "ok"; }
                }
                """,
                null);
        assertThatThrownBy(() -> scanner.scan(root)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void serviceWithoutRoutesFailsClosed(@TempDir Path root) throws IOException {
        Path svc = Files.createDirectories(root.resolve("lonely-service/src/main/java"));
        Files.writeString(
                svc.resolve("NoController.java"), "public class NoController {}", StandardCharsets.UTF_8);
        Files.createDirectories(root.resolve("lonely-service/src/main/resources"));
        Files.writeString(
                root.resolve("lonely-service/src/main/resources/application.example.properties"),
                "spring.application.name=lonely-service\nserver.port=8099\n",
                StandardCharsets.UTF_8);
        assertThatThrownBy(() -> scanner.scan(root)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void databaseWithoutMigrationsFailsClosed(@TempDir Path root) throws IOException {
        writeService(
                root,
                "order-service",
                """
                spring.application.name=order-service
                server.port=8082
                spring.datasource.url=jdbc:postgresql://localhost:5432/order_db
                spring.datasource.username=order_app
                """,
                """
                @RequestMapping("/api/v1/orders")
                public class OrderController {
                    @PostMapping
                    public String checkout() { return "ok"; }
                }
                """,
                null);
        // No migration directory was created: the declared database has no schema source.
        assertThatThrownBy(() -> scanner.scan(root)).isInstanceOf(IllegalArgumentException.class);
    }

    private static void writeService(
            Path root, String dir, String properties, String controller, String migration)
            throws IOException {
        Path svc = Files.createDirectories(root.resolve(dir));
        Files.writeString(
                Files.createDirectories(svc.resolve("src/main/resources"))
                        .resolve("application.example.properties"),
                properties,
                StandardCharsets.UTF_8);
        Path javaDir = Files.createDirectories(svc.resolve("src/main/java/com/example"));
        Files.writeString(javaDir.resolve("Controller.java"), controller, StandardCharsets.UTF_8);
        if (migration != null) {
            Path sqlDir = Files.createDirectories(svc.resolve("src/main/resources/db/migration"));
            Files.writeString(sqlDir.resolve("V1__init.sql"), migration, StandardCharsets.UTF_8);
        }
    }
}
