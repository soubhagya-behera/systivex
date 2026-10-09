package com.soubhagya.systivex.observation;

import com.soubhagya.systivex.twin.model.SystemEntityType;
import com.soubhagya.systivex.twin.model.SystemRelationshipType;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Phase 4 read-only scanner: reads the target-service repository (service
 * directories, {@code application.example.properties} files, controller
 * route annotations, Flyway migration SQL) and maps what it finds onto the
 * existing twin entity/relationship enums.
 *
 * <p>Read-only by construction: it opens files for reading and never writes,
 * moves, deletes, or executes anything under the repository root. It reads
 * only the tracked example configuration (never live {@code
 * application.properties}, never environment values), so datasource
 * passwords and other local secrets cannot flow into the twin.
 *
 * <p>Fail-closed: any missing, malformed, or inconsistent fact aborts the
 * whole scan with {@link IllegalArgumentException} before anything is
 * persisted. The sync service calls this first, so a failed scan can never
 * leave a partial graph.
 */
@Component
public class TargetRepositoryScanner {

    static final String CONNECTOR_ID = "repository-connector";
    static final String CONNECTOR_VERSION = "phase4-v1";
    static final String ENVIRONMENT = "dev";

    private static final String EXAMPLE_PROPERTIES =
            "src/main/resources/application.example.properties";
    private static final Pattern PLACEHOLDER_DEFAULT = Pattern.compile("^\\$\\{[^:{}]+:(.*)\\}$");
    private static final Pattern PORT_NUMBER = Pattern.compile(":(\\d+)(?=/|$)");
    private static final Pattern JDBC_DB = Pattern.compile("/([^/?]+)(\\?.*)?$");
    private static final Pattern CLASS_MAPPING = Pattern.compile("@RequestMapping\\(\"([^\"]*)\"\\)");
    private static final Pattern METHOD_MAPPING =
            Pattern.compile("@(Get|Post|Put|Delete|Patch)Mapping(?:\\(\"([^\"]*)\"\\))?");

    /**
     * Scans {@code root} and returns the complete twin graph. Throws {@link
     * IllegalArgumentException} for an unusable root or any inconsistent
     * repository fact.
     */
    public DiscoveryResult scan(Path root) {
        Path canonical = checkRoot(root);
        List<ServiceFacts> services = discoverServices(canonical);
        if (services.isEmpty()) {
            throw new IllegalArgumentException(
                    "No target services discovered under " + canonical
                            + ": expected service directories containing "
                            + EXAMPLE_PROPERTIES);
        }
        Map<String, ServiceFacts> byRef = new LinkedHashMap<>();
        for (ServiceFacts service : services) {
            readConfiguration(canonical, service);
            readRoutes(canonical, service);
            readTables(canonical, service);
            String ref = serviceRef(service.name);
            if (byRef.put(ref, service) != null) {
                throw new IllegalArgumentException(
                        "Duplicate target service name in repository: " + service.name);
            }
        }
        Map<Integer, ServiceFacts> byPort = new TreeMap<>();
        for (ServiceFacts service : services) {
            if (byPort.put(service.port, service) != null) {
                throw new IllegalArgumentException(
                        "Ambiguous target service port " + service.port + " in repository");
            }
        }
        return buildGraph(services, byPort);
    }

    private Path checkRoot(Path root) {
        if (root == null) {
            throw new IllegalArgumentException(
                    "Repository root is not configured: set systivex.observation.repository-root "
                            + "(SYSTIVEX_OBSERVATION_REPOSITORY_ROOT) or pass repositoryRoot");
        }
        Path canonical;
        try {
            canonical = root.toAbsolutePath().normalize();
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid repository root: " + root);
        }
        if (!Files.isDirectory(canonical) || !Files.isReadable(canonical)) {
            throw new IllegalArgumentException(
                    "Repository root is not a readable directory: " + canonical);
        }
        return canonical;
    }

    private List<ServiceFacts> discoverServices(Path root) {
        List<ServiceFacts> services = new ArrayList<>();
        try (DirectoryStream<Path> dirs = Files.newDirectoryStream(root)) {
            for (Path dir : dirs) {
                if (Files.isDirectory(dir)
                        && Files.isRegularFile(dir.resolve(EXAMPLE_PROPERTIES))) {
                    ServiceFacts facts = new ServiceFacts();
                    facts.directory = dir.getFileName().toString();
                    services.add(facts);
                }
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Cannot list repository root " + root, e);
        }
        services.sort((a, b) -> a.directory.compareTo(b.directory));
        return services;
    }

    private void readConfiguration(Path root, ServiceFacts service) {
        Path file = root.resolve(service.directory).resolve(EXAMPLE_PROPERTIES);
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(file)) {
            props.load(in);
        } catch (IOException e) {
            throw new IllegalArgumentException(
                    "Cannot read service configuration " + file, e);
        }
        service.name = required(props, file, "spring.application.name");
        service.port = parsePort(required(props, file, "server.port"), file);
        String jdbcUrl = expandDefault(props.getProperty("spring.datasource.url", "").trim());
        String dbUser = expandDefault(props.getProperty("spring.datasource.username", "").trim());
        if (!jdbcUrl.isEmpty()) {
            service.database = parseDatabaseName(jdbcUrl, file);
            if (dbUser.isEmpty()) {
                throw new IllegalArgumentException(
                        "Service " + service.name + " declares a datasource URL but no username in "
                                + file);
            }
            service.dbOwner = dbUser;
        }
        for (String key : props.stringPropertyNames()) {
            if (key.startsWith("app.") && key.endsWith(".url")) {
                String url = expandDefault(props.getProperty(key, "").trim());
                Matcher port = PORT_NUMBER.matcher(url);
                if (!port.find()) {
                    throw new IllegalArgumentException(
                            "Service " + service.name + " has a downstream URL without a port in "
                                    + file + " (" + key + ")");
                }
                Downstream downstream = new Downstream();
                downstream.configKey = key;
                downstream.port = Integer.parseInt(port.group(1));
                service.downstream.add(downstream);
            }
        }
    }

    private void readRoutes(Path root, ServiceFacts service) {
        Path javaRoot = root.resolve(service.directory).resolve("src/main/java");
        if (!Files.isDirectory(javaRoot)) {
            throw new IllegalArgumentException(
                    "Service " + service.name + " has no Java sources under " + javaRoot);
        }
        List<Path> javaFiles;
        try (var files = Files.walk(javaRoot)) {
            javaFiles =
                    files.filter(Files::isRegularFile)
                            .filter(p -> p.toString().endsWith(".java"))
                            .sorted()
                            .toList();
        } catch (IOException e) {
            throw new IllegalArgumentException(
                    "Cannot read Java sources for service " + service.name, e);
        }
        for (Path file : javaFiles) {
            String code;
            try {
                code = Files.readString(file, StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new IllegalArgumentException("Cannot read source file " + file, e);
            }
            String base = "";
            Matcher classMapping = CLASS_MAPPING.matcher(code);
            if (classMapping.find()) {
                base = classMapping.group(1).trim();
            }
            Matcher method = METHOD_MAPPING.matcher(code);
            while (method.find()) {
                String sub = method.group(2) == null ? "" : method.group(2).trim();
                service.routes.add(new Route(method.group(1).toUpperCase(), join(base, sub)));
            }
        }
        if (service.routes.isEmpty()) {
            throw new IllegalArgumentException(
                    "Service " + service.name + " exposes no HTTP routes: no controller mappings found");
        }
    }

    private void readTables(Path root, ServiceFacts service) {
        Path migrationDir =
                root.resolve(service.directory).resolve("src/main/resources/db/migration");
        if (service.database == null) {
            return;
        }
        if (!Files.isDirectory(migrationDir)) {
            throw new IllegalArgumentException(
                    "Service " + service.name + " owns database " + service.database
                            + " but has no Flyway migrations under " + migrationDir);
        }
        Pattern tableName = Pattern.compile(
                "(?i)CREATE\\s+TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?[\"'`]?(\\w[\\w.]*?)[\"'`]?\\s*\\(");
        List<Path> migrationFiles;
        try (var files = Files.walk(migrationDir)) {
            migrationFiles =
                    files.filter(Files::isRegularFile)
                            .filter(p -> p.toString().endsWith(".sql"))
                            .sorted()
                            .toList();
        } catch (IOException e) {
            throw new IllegalArgumentException(
                    "Cannot read migrations for service " + service.name, e);
        }
        for (Path file : migrationFiles) {
            String sql;
            try {
                sql = Files.readString(file, StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new IllegalArgumentException("Cannot read migration file " + file, e);
            }
            Matcher matcher = tableName.matcher(sql);
            while (matcher.find()) {
                String raw = matcher.group(1);
                String table = raw.contains(".") ? raw.substring(raw.lastIndexOf('.') + 1) : raw;
                if (!service.tables.contains(table)) {
                    service.tables.add(table);
                }
            }
        }
        if (service.tables.isEmpty()) {
            throw new IllegalArgumentException(
                    "Service " + service.name + " owns database " + service.database
                            + " but its migrations create no tables");
        }
    }

    private DiscoveryResult buildGraph(List<ServiceFacts> services, Map<Integer, ServiceFacts> byPort) {
        List<DiscoveredEntity> entities = new ArrayList<>();
        List<DiscoveredRelationship> relationships = new ArrayList<>();
        for (ServiceFacts service : services) {
            entities.add(
                    new DiscoveredEntity(
                            SystemEntityType.SERVICE,
                            service.name,
                            serviceRef(service.name),
                            ENVIRONMENT,
                            serviceMetadata(service)));
            if (service.database != null) {
                entities.add(
                        new DiscoveredEntity(
                                SystemEntityType.DATABASE,
                                service.database,
                                databaseRef(service.database),
                                ENVIRONMENT,
                                Map.of(
                                        "owner",
                                        service.dbOwner,
                                        "ownedByService",
                                        service.name,
                                        "managedBy",
                                        CONNECTOR_ID,
                                        "connectorVersion",
                                        CONNECTOR_VERSION)));
                for (String table : service.tables) {
                    entities.add(
                            new DiscoveredEntity(
                                    SystemEntityType.TABLE,
                                    table,
                                    tableRef(service.database, table),
                                    ENVIRONMENT,
                                    Map.of(
                                            "database",
                                            service.database,
                                            "managedBy",
                                            CONNECTOR_ID,
                                            "connectorVersion",
                                            CONNECTOR_VERSION)));
                }
            }
            for (Route route : service.routes) {
                entities.add(
                        new DiscoveredEntity(
                                SystemEntityType.API,
                                route.method() + " " + route.path(),
                                apiRef(service.name, route),
                                ENVIRONMENT,
                                Map.of(
                                        "service",
                                        service.name,
                                        "method",
                                        route.method(),
                                        "path",
                                        route.path(),
                                        "managedBy",
                                        CONNECTOR_ID,
                                        "connectorVersion",
                                        CONNECTOR_VERSION)));
            }
        }
        for (ServiceFacts service : services) {
            for (Downstream downstream : service.downstream) {
                ServiceFacts target = byPort.get(downstream.port);
                if (target == null) {
                    throw new IllegalArgumentException(
                            "Service " + service.name + " depends on unknown port "
                                    + downstream.port + " (" + downstream.configKey + ")");
                }
                if (target == service) {
                    throw new IllegalArgumentException(
                            "Service " + service.name + " depends on itself ("
                                    + downstream.configKey + ")");
                }
                relationships.add(
                        new DiscoveredRelationship(
                                serviceRef(service.name),
                                serviceRef(target.name),
                                SystemRelationshipType.CALLS));
            }
            if (service.database != null) {
                relationships.add(
                        new DiscoveredRelationship(
                                serviceRef(service.name),
                                databaseRef(service.database),
                                SystemRelationshipType.DEPENDS_ON));
                for (String table : service.tables) {
                    relationships.add(
                            new DiscoveredRelationship(
                                    databaseRef(service.database),
                                    tableRef(service.database, table),
                                    SystemRelationshipType.CONTAINS));
                    relationships.add(
                            new DiscoveredRelationship(
                                    serviceRef(service.name),
                                    tableRef(service.database, table),
                                    SystemRelationshipType.WRITES));
                }
            }
            for (Route route : service.routes) {
                relationships.add(
                        new DiscoveredRelationship(
                                serviceRef(service.name),
                                apiRef(service.name, route),
                                SystemRelationshipType.EXPOSES));
            }
        }
        validate(entities, relationships);
        return new DiscoveryResult(entities, relationships);
    }

    private void validate(
            List<DiscoveredEntity> entities, List<DiscoveredRelationship> relationships) {
        Map<String, DiscoveredEntity> byRef = new LinkedHashMap<>();
        for (DiscoveredEntity entity : entities) {
            if (entity.name() == null || entity.name().isBlank()
                    || entity.externalRef() == null
                    || entity.externalRef().isBlank()) {
                throw new IllegalArgumentException("Discovery produced an entity without identity");
            }
            if (byRef.put(entity.externalRef(), entity) != null) {
                throw new IllegalArgumentException(
                        "Discovery produced a duplicate entity ref: " + entity.externalRef());
            }
        }
        for (DiscoveredRelationship relationship : relationships) {
            if (!byRef.containsKey(relationship.sourceRef())
                    || !byRef.containsKey(relationship.targetRef())) {
                throw new IllegalArgumentException(
                        "Discovery produced an edge with an unknown endpoint: "
                                + relationship.sourceRef() + " -> " + relationship.targetRef());
            }
            if (relationship.sourceRef().equals(relationship.targetRef())) {
                throw new IllegalArgumentException(
                        "Discovery produced a self-referencing edge: " + relationship.sourceRef());
            }
        }
    }

    private Map<String, Object> serviceMetadata(ServiceFacts service) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("port", service.port);
        metadata.put("directory", service.directory);
        if (service.database != null) {
            metadata.put("database", service.database);
            metadata.put("dbOwner", service.dbOwner);
        }
        if (!service.downstream.isEmpty()) {
            List<String> downstream = new ArrayList<>();
            for (Downstream d : service.downstream) {
                downstream.add(d.configKey + " -> port " + d.port);
            }
            metadata.put("downstream", downstream);
        }
        metadata.put("managedBy", CONNECTOR_ID);
        metadata.put("connectorVersion", CONNECTOR_VERSION);
        return metadata;
    }

    static String serviceRef(String serviceName) {
        return "target-service:" + serviceName;
    }

    static String databaseRef(String database) {
        return "target-database:" + database;
    }

    static String tableRef(String database, String table) {
        return "target-table:" + database + "." + table;
    }

    static String apiRef(String serviceName, Route route) {
        return "target-api:" + serviceName + ":" + route.method() + " " + route.path();
    }

    private static String required(Properties props, Path file, String key) {
        String value = expandDefault(props.getProperty(key, "").trim());
        if (value.isEmpty()) {
            throw new IllegalArgumentException(
                    "Required property " + key + " is missing in " + file);
        }
        return value;
    }

    private static int parsePort(String value, Path file) {
        try {
            int port = Integer.parseInt(value);
            if (port < 1 || port > 65535) {
                throw new IllegalArgumentException(
                        "Invalid server.port " + value + " in " + file);
            }
            return port;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid server.port " + value + " in " + file);
        }
    }

    private static String parseDatabaseName(String jdbcUrl, Path file) {
        Matcher matcher = JDBC_DB.matcher(jdbcUrl.substring(jdbcUrl.indexOf("://") + 3));
        if (!matcher.find()) {
            throw new IllegalArgumentException(
                    "Cannot determine database name from datasource URL in " + file);
        }
        return matcher.group(1);
    }

    /** Resolves a {@code ${ENV:default}} placeholder to its default without touching the environment. */
    static String expandDefault(String value) {
        Matcher matcher = PLACEHOLDER_DEFAULT.matcher(value);
        return matcher.matches() ? matcher.group(1) : value;
    }

    private static String join(String base, String sub) {
        String path = (base + "/" + sub).replaceAll("/+", "/");
        if (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        return path.isEmpty() ? "/" : path;
    }

    static final class ServiceFacts {
        String directory;
        String name;
        int port;
        String database;
        String dbOwner;
        final List<Downstream> downstream = new ArrayList<>();
        final List<Route> routes = new ArrayList<>();
        final List<String> tables = new ArrayList<>();
    }

    static final class Downstream {
        String configKey;
        int port;
    }

    record Route(String method, String path) {}
}
