package com.neueda.e2e.support;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.output.OutputFrame;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.containers.wait.strategy.WaitAllStrategy;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/**
 * The whole system in containers on a private network, started once per test run and removed
 * by Testcontainers afterwards. Host ports are random, so it runs beside a local docker-compose stack.
 *
 * Everything is real except Alpaca, which WireMock stands in for with fixed quotes: live quotes
 * depend on market hours, use up the daily quota and need credentials.
 *
 * Service images are built from each module's Dockerfile, or taken prebuilt from
 * -De2e.image.app / -De2e.image.auth / -De2e.image.engine (Jenkins passes the images it built).
 * Container logs go to target/e2e-logs.
 */
public final class Stack {

    private static final Path ROOT = Path.of(System.getProperty("e2e.root", "..")).toAbsolutePath().normalize();
    private static final Path LOGS = Path.of("target", "e2e-logs");
    private static final Duration STARTUP = Duration.ofMinutes(3);

    private static final String DB_NAME = "technova";
    private static final String DB_USER = "technova";
    private static final String DB_PASSWORD = "e2e-password";
    private static final String KAFKA = "kafka:19092";

    private static String appUrl;
    private static String authUrl;
    private static String alpacaUrl;
    private static String jdbcUrl;

    private Stack() {
    }

    /** Starts everything once; later calls return immediately. */
    public static synchronized void start() {
        if (appUrl != null) {
            return;
        }
        Network network = Network.newNetwork();
        Map<String, String> sharedEnv = Map.of(
            "DB_HOST", "db", "DB_PORT", "5432", "DB_NAME", DB_NAME,
            "DB_USER", DB_USER, "DB_PASSWORD", DB_PASSWORD, "JWT_SECRET", randomSecret());

        PostgreSQLContainer<?> db = postgres(network);
        KafkaContainer kafka = kafka(network);
        GenericContainer<?> alpaca = alpacaStub(network);
        GenericContainer<?> auth = authService(network, sharedEnv);
        GenericContainer<?> app = app(network, sharedEnv);
        GenericContainer<?> engine = executionEngine(network);

        Stream.of(db, kafka, alpaca).parallel().forEach(GenericContainer::start);
        // app creates the order topics, so it starts before the engine subscribes to them
        Stream.of(auth, app).parallel().forEach(GenericContainer::start);
        engine.start();

        authUrl = "http://" + auth.getHost() + ":" + auth.getMappedPort(8082) + "/auth";
        appUrl = "http://" + app.getHost() + ":" + app.getMappedPort(8081) + "/api";
        alpacaUrl = "http://" + alpaca.getHost() + ":" + alpaca.getMappedPort(8080);
        jdbcUrl = db.getJdbcUrl();
    }

    public static String appUrl() {
        return appUrl;
    }

    public static String authUrl() {
        return authUrl;
    }

    /** The Alpaca stub, whose /__admin API lets a test move the market. */
    static String alpacaUrl() {
        return alpacaUrl;
    }

    /** Test setup only, for state the API cannot create; tests assert through HTTP. */
    static Connection openDatabase() throws SQLException {
        return DriverManager.getConnection(jdbcUrl, DB_USER, DB_PASSWORD);
    }

    // ---- containers ----

    /** Postgres with the real migrations, plus reference prices the data pipeline would load. */
    private static PostgreSQLContainer<?> postgres(Network network) {
        return new PostgreSQLContainer<>("postgres:16-alpine")
            .withNetwork(network).withNetworkAliases("db").withLogConsumer(logTo("db"))
            .withDatabaseName(DB_NAME).withUsername(DB_USER).withPassword(DB_PASSWORD)
            .withCopyFileToContainer(MountableFile.forHostPath(ROOT.resolve("db/migrations")),
                "/docker-entrypoint-initdb.d/")
            .withCopyFileToContainer(MountableFile.forClasspathResource("db/V999_e2e_prices.sql"),
                "/docker-entrypoint-initdb.d/V999_e2e_prices.sql");
    }

    private static KafkaContainer kafka(Network network) {
        return new KafkaContainer("apache/kafka:4.0.0")
            .withNetwork(network).withListener(KAFKA).withLogConsumer(logTo("kafka"))
            // as in docker-compose: topics come from the services' NewTopic beans
            .withEnv("KAFKA_AUTO_CREATE_TOPICS_ENABLE", "false");
    }

    /** WireMock serving the quotes in wiremock/mappings; see {@link AlpacaStub}. */
    private static GenericContainer<?> alpacaStub(Network network) {
        return new GenericContainer<>("wiremock/wiremock:3.13.1")
            .withNetwork(network).withNetworkAliases("alpaca").withLogConsumer(logTo("alpaca"))
            .withCopyFileToContainer(MountableFile.forClasspathResource("wiremock/mappings"), "/home/wiremock/mappings")
            .withExposedPorts(8080)
            .waitingFor(Wait.forHttp("/__admin/health"));
    }

    private static GenericContainer<?> authService(Network network, Map<String, String> env) {
        return service("auth", "auth", network)
            .withEnv(env)
            .withExposedPorts(8082)
            // NestJS, so no Spring "Started ..." log line; ready once its health endpoint answers
            .waitingFor(Wait.forHttp("/auth/health").forPort(8082).withStartupTimeout(STARTUP));
    }

    private static GenericContainer<?> app(Network network, Map<String, String> env) {
        return service("app", "app", network)
            .withEnv(env)
            .withEnv("SPRING_KAFKA_BOOTSTRAP_SERVERS", KAFKA)
            .withExposedPorts(8081);
    }

    private static GenericContainer<?> executionEngine(Network network) {
        return service("engine", "execution-engine", network)
            .withEnv("SPRING_KAFKA_BOOTSTRAP_SERVERS", KAFKA)
            // poll the stub every second; the quota check would refuse that rate against real Alpaca
            .withEnv("SPRING_APPLICATION_JSON", """
                {"market-data": {"base-url": "http://alpaca:8080", "interval-seconds": 1,
                 "daily-quota": 1000000, "key-id": "e2e", "secret-key": "e2e"}}""")
            // ready once both consumers own their partitions, so no early order or quote is missed
            .waitingFor(new WaitAllStrategy()
                .withStrategy(Wait.forLogMessage(".*execution-engine: partitions assigned: \\[order-request.*", 1))
                .withStrategy(Wait.forLogMessage(".*execution-engine-quotes: partitions assigned: \\[market-data.*", 1))
                .withStartupTimeout(STARTUP));
    }

    /** One of our services, from a prebuilt image or its module's Dockerfile. */
    private static GenericContainer<?> service(String name, String module, Network network) {
        String prebuilt = System.getProperty("e2e.image." + name);
        GenericContainer<?> container = prebuilt != null
            ? new GenericContainer<>(DockerImageName.parse(prebuilt))
            : new GenericContainer<>(new ImageFromDockerfile("tech-nova-e2e-" + name, false)
                .withFileFromPath(".", ROOT.resolve(module)));
        return container
            .withNetwork(network)
            .withLogConsumer(logTo(name))
            .waitingFor(Wait.forLogMessage(".*Started \\w+ in .*", 1).withStartupTimeout(STARTUP));
    }

    // ---- helpers ----

    private static Consumer<OutputFrame> logTo(String name) {
        try {
            Files.createDirectories(LOGS);
            PrintWriter out = new PrintWriter(Files.newBufferedWriter(LOGS.resolve(name + ".log")));
            return frame -> {
                out.print(frame.getUtf8String());
                out.flush();
            };
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String randomSecret() {
        byte[] bytes = new byte[48];
        new SecureRandom().nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }
}
