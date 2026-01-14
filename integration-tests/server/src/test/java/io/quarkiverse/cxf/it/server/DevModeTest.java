package io.quarkiverse.cxf.it.server;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.jboss.logging.Logger;
import org.junit.jupiter.api.Test;
import org.l2x6.cli.assured.CommandProcess;
import org.l2x6.mvn.assured.Mvn;

import io.restassured.RestAssured;

public class DevModeTest {

    private static final Logger log = Logger.getLogger(DevModeTest.class);

    @Test
    public void devMode() throws IOException, InterruptedException {
        final String quarkusVersion = getQuarkusVersion();
        final Path srcMainJava = Path.of("src/main/java");
        final String artifactId = "quarkus-cxf-integration-test-dev-mode";
        final Path tempProject = Path.of("target/" + DevModeTest.class.getSimpleName() + "-" + UUID.randomUUID())
                .resolve(artifactId)
                .toAbsolutePath().normalize();
        try {
            Files.createDirectories(tempProject.getParent());
        } catch (IOException e) {
            throw new UncheckedIOException("Could not create " + tempProject.getParent(), e);
        }

        final Mvn mvn = Mvn.fromMvnw(Path.of(".").toAbsolutePath().normalize()).installIfNeeded();
        mvn
                .args(
                        "io.quarkus.platform:quarkus-maven-plugin:" + quarkusVersion + ":create",
                        "-DprojectGroupId=io.quarkiverse.cxf",
                        "-DprojectArtifactId=" + artifactId,
                        "-Dextensions=io.quarkiverse.cxf:quarkus-cxf")
                .cd(tempProject.getParent())
                .then()
                .stdout().log()
                .stderr().log()
                .execute()
                .assertSuccess();

        /* Copy source files */
        final Path tempSrcMainJava = tempProject.resolve("src/main/java");
        Stream.of(
                Fruit.class,
                FruitService.class,
                FruitServiceImpl.class)
                .forEach(cl -> {
                    final String relJavaFile = relJavaFile(cl);
                    Path src = srcMainJava.resolve(relJavaFile);
                    Path dest = tempSrcMainJava.resolve(relJavaFile);
                    try {
                        Files.createDirectories(dest.getParent());
                    } catch (IOException e) {
                        throw new UncheckedIOException("Could not create " + dest.getParent(), e);
                    }
                    try {
                        Files.copy(src, dest);
                    } catch (IOException e) {
                        throw new UncheckedIOException("Could not copy " + src + " to " + dest, e);
                    }
                });
        final Path appProps = tempProject.resolve("src/main/resources/application.properties");
        Files.createDirectories(appProps.getParent());
        Files.write(appProps,
                """
                        quarkus.cxf.endpoint."/fruits".implementor = io.quarkiverse.cxf.it.server.FruitServiceImpl
                        quarkus.cxf.endpoint."/fruits".logging.enabled = pretty
                        """.getBytes(StandardCharsets.UTF_8));

        /* Run in dev mode */
        CountDownLatch started = new CountDownLatch(1);

        CommandProcess mvnProcess = null;
        try {
            mvnProcess = mvn
                    .args("quarkus:dev")
                    .cd(tempProject)
                    .then()
                    .stdout()
                    .log(line -> {
                        log.info(line);
                        if (line.contains("Installed features: [")) {
                            started.countDown();
                        }
                    })
                    .stderr().log()
                    .start();

            started.await(20, TimeUnit.SECONDS);

            RestAssured.given()
                    .header("Content-Type", "text/xml")
                    .body("""
                            <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:ser="http://server.it.cxf.quarkiverse.io/">
                                <soapenv:Header/>
                                <soapenv:Body>
                                    <ser:add>
                                        <arg0>
                                            <description>foo</description>
                                            <name>bar</name>
                                        </arg0>
                                    </ser:add>
                                </soapenv:Body>
                            </soapenv:Envelope>
                            """)
                    .post("http://localhost:8080/services/fruits")
                    .then()
                    .statusCode(200)
                    .body(containsString("<description>bar</description>"));

            /* Change something in Fruit.java */
            final Path fruitJava = tempSrcMainJava.resolve(relJavaFile(Fruit.class));
            Files.writeString(
                    fruitJava,
                    Files.readString(fruitJava, StandardCharsets.UTF_8)
                            .replace("return description;", "return \"Modified: \" + description;"),
                    StandardCharsets.UTF_8);

            RestAssured.given()
                    .header("Content-Type", "text/xml")
                    .body("""
                            <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:ser="http://server.it.cxf.quarkiverse.io/">
                                <soapenv:Header/>
                                <soapenv:Body>
                                    <ser:add>
                                        <arg0>
                                            <description>bam</description>
                                            <name>ban</name>
                                        </arg0>
                                    </ser:add>
                                </soapenv:Body>
                            </soapenv:Envelope>
                            """)
                    .post("http://localhost:8080/services/fruits")
                    .then()
                    .statusCode(200)
                    .body(containsString("<description>Modified: bam</description>"));

        } finally {
            if (mvnProcess != null) {
                mvnProcess.kill(true);
            }
        }

    }

    static String relJavaFile(Class<? extends Object> cl) {
        return cl.getName().replace('.', '/') + ".java";
    }

    static String getQuarkusVersion() {
        Properties props = new Properties();
        try (InputStream is = Thread.currentThread()
                .getContextClassLoader()
                .getResourceAsStream("META-INF/maven/io.quarkus/quarkus-core/pom.properties")) {
            props.load(is);
            return props.getProperty("version");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
