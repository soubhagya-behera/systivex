import { execFileSync } from "node:child_process";
import { readFileSync } from "node:fs";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const root = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const checkRunning = process.argv.includes("--running");
const failures = [];

const managementImports =
  "src/main/resources/META-INF/spring/" +
  "org.springframework.boot.actuate.autoconfigure.web." +
  "ManagementContextConfiguration.imports";

const services = [
  {
    name: "gateway-service",
    example:
      "backend/target-services/gateway-service/src/main/resources/application.example.properties",
    guard:
      "backend/target-services/gateway-service/src/main/java/com/soubhagya/systivex/gateway/LoopbackBindingConfiguration.java",
    managementGuard:
      "backend/target-services/gateway-service/src/main/java/com/soubhagya/systivex/management/gateway/GatewayManagementLoopbackConfiguration.java",
    managementImports: `backend/target-services/gateway-service/${managementImports}`,
    managementClass:
      "com.soubhagya.systivex.management.gateway.GatewayManagementLoopbackConfiguration"
  },
  {
    name: "order-service",
    example:
      "backend/target-services/order-service/src/main/resources/application.example.properties",
    guard:
      "backend/target-services/order-service/src/main/java/com/soubhagya/systivex/order/LoopbackBindingConfiguration.java",
    managementGuard:
      "backend/target-services/order-service/src/main/java/com/soubhagya/systivex/management/order/OrderManagementLoopbackConfiguration.java",
    managementImports: `backend/target-services/order-service/${managementImports}`,
    managementClass:
      "com.soubhagya.systivex.management.order.OrderManagementLoopbackConfiguration"
  },
  {
    name: "payment-service",
    example:
      "backend/target-services/payment-service/src/main/resources/application.example.properties",
    guard:
      "backend/target-services/payment-service/src/main/java/com/soubhagya/systivex/payment/LoopbackBindingConfiguration.java",
    managementGuard:
      "backend/target-services/payment-service/src/main/java/com/soubhagya/systivex/management/payment/PaymentManagementLoopbackConfiguration.java",
    managementImports: `backend/target-services/payment-service/${managementImports}`,
    managementClass:
      "com.soubhagya.systivex.management.payment.PaymentManagementLoopbackConfiguration"
  },
  {
    name: "inventory-service",
    example:
      "backend/target-services/inventory-service/src/main/resources/application.example.properties",
    guard:
      "backend/target-services/inventory-service/src/main/java/com/soubhagya/systivex/inventory/LoopbackBindingConfiguration.java",
    managementGuard:
      "backend/target-services/inventory-service/src/main/java/com/soubhagya/systivex/management/inventory/InventoryManagementLoopbackConfiguration.java",
    managementImports: `backend/target-services/inventory-service/${managementImports}`,
    managementClass:
      "com.soubhagya.systivex.management.inventory.InventoryManagementLoopbackConfiguration"
  }
];

const composePorts = [3000, 3100, 4317, 4318, 8889, 9090, 13133];
const targetServicePorts = [8081, 8082, 8083, 8084];
const expectedPorts = [...composePorts, ...targetServicePorts];

function record(ok, message) {
  if (ok) {
    console.log(`[ok] ${message}`);
  } else {
    failures.push(message);
    console.error(`[fail] ${message}`);
  }
}

function read(path) {
  return readFileSync(join(root, path), "utf8");
}

function hasAll(source, tokens) {
  return tokens.every((token) => source.includes(token));
}

for (const service of services) {
  const example = read(service.example);
  const hasLoopback = /^server\.address\s*=\s*127\.0\.0\.1\s*$/m.test(example);
  record(hasLoopback, `${service.name} example binds 127.0.0.1`);

  let guard;
  try {
    guard = read(service.guard);
  } catch {
    record(false, `${service.name} loopback guard exists`);
  }
  if (guard) {
    record(true, `${service.name} loopback guard exists`);
    record(
      hasAll(guard, [
        "class LoopbackWebServerFactoryCustomizer",
        "setAddress(resolve(serverProperties.getAddress()))",
        "server.address must be a loopback address",
        "Ordered.LOWEST_PRECEDENCE"
      ]),
      `${service.name} loopback guard enforces the main listener`
    );
  }

  let managementGuard;
  try {
    managementGuard = read(service.managementGuard);
  } catch {
    record(false, `${service.name} management loopback guard exists`);
  }
  if (managementGuard) {
    record(
      true,
      `${service.name} management loopback guard exists`
    );
    record(
      hasAll(managementGuard, [
        "@ManagementContextConfiguration(value = ManagementContextType.CHILD",
        "setAddress(resolve(managementServerProperties.getAddress()))",
        "management.server.address must be a loopback address",
        "Ordered.LOWEST_PRECEDENCE"
      ]),
      `${service.name} management guard enforces the child listener`
    );
  }

  let imports;
  try {
    imports = read(service.managementImports);
  } catch {
    record(false, `${service.name} management context import exists`);
  }
  if (imports) {
    record(
      imports.split(/\r?\n/).includes(service.managementClass),
      `${service.name} registers its management guard`
    );
  }
}

let compose;
try {
  const output = execFileSync(
    "docker",
    ["compose", "-f", "observability/docker-compose.yml", "config", "--format", "json"],
    { cwd: root, encoding: "utf8" }
  );
  compose = JSON.parse(output);
} catch (error) {
  record(false, `docker compose config failed: ${error.message}`);
}

if (compose) {
  const seen = new Map();
  for (const [serviceName, service] of Object.entries(compose.services ?? {})) {
    for (const port of service.ports ?? []) {
      const published = Number(port.published);
      seen.set(published, (seen.get(published) ?? 0) + 1);
      record(
        port.host_ip === "127.0.0.1",
        `${serviceName} publishes ${published} on ${port.host_ip ?? "<all interfaces>"}`
      );
    }
  }

  for (const port of composePorts) {
    record(seen.get(port) === 1, `compose publishes expected port ${port} exactly once`);
  }

  for (const [port, count] of seen) {
    if (!composePorts.includes(port)) {
      record(false, `compose publishes unexpected port ${port} (${count} mapping(s))`);
    }
  }
}

if (checkRunning) {
  let listeners;
  try {
    listeners = readListeners();
  } catch (error) {
    record(false, `running listener check failed: ${error.message}`);
  }

  if (listeners) {
    const byPort = new Map();
    for (const listener of listeners) {
      const port = Number(listener.port);
      if (!byPort.has(port)) {
        byPort.set(port, new Set());
      }
      byPort.get(port).add(listener.address);
    }

    for (const port of expectedPorts) {
      const addresses = byPort.get(port);
      record(addresses !== undefined && addresses.size > 0, `port ${port} is listening`);
      if (addresses) {
        for (const address of addresses) {
          record(isLoopbackAddress(address), `port ${port} listens on loopback (${address})`);
        }
      }
    }
  }
}

function readListeners() {
  if (process.platform === "win32") {
    const command =
      "Get-NetTCPConnection -State Listen -ErrorAction SilentlyContinue | " +
      `Where-Object { $_.LocalPort -in ${expectedPorts.join(",")} } | ` +
      "Select-Object LocalAddress,LocalPort | ConvertTo-Json -Compress";
    const output = execFileSync("powershell.exe", ["-NoProfile", "-Command", command], {
      encoding: "utf8"
    }).trim();
    if (!output) {
      return [];
    }
    const parsed = JSON.parse(output);
    const rows = Array.isArray(parsed) ? parsed : [parsed];
    return rows.map((row) => ({ address: row.LocalAddress, port: row.LocalPort }));
  }

  const output = execFileSync("ss", ["-ltnH"], { encoding: "utf8" });
  return output
    .split(/\r?\n/)
    .map((line) => line.trim().split(/\s+/))
    .filter((fields) => fields.length >= 4)
    .map((fields) => {
      const local = fields[3];
      const separator = local.lastIndexOf(":");
      return {
        address: local.slice(0, separator).replace(/^\[|\]$/g, ""),
        port: Number(local.slice(separator + 1))
      };
    });
}

function isLoopbackAddress(address) {
  return (
    address === "127.0.0.1" ||
    address === "::1" ||
    address === "::ffff:127.0.0.1" ||
    address.startsWith("127.")
  );
}

if (failures.length > 0) {
  console.error(`Loopback verification failed with ${failures.length} error(s).`);
  process.exit(1);
}

console.log("Loopback verification passed.");
