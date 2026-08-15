package dev.anand.errorlogging;

import dev.anand.claudeskills.exception.GlobalExceptionHandler;
import dev.anand.claudeskills.logging.RequestIdFilter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Boots a real (DB-free) Spring Boot web context so the actual logback-spring.xml is
 * applied, then triggers an unexpected exception through the real GlobalExceptionHandler
 * and asserts the full stack trace — with the requestId set by RequestIdFilter — lands
 * in error.log. Lives outside dev.anand.claudeskills so it is never component-scanned
 * by the app's full-context tests.
 */
@SpringBootTest(
        classes = ErrorLoggingEndToEndTest.TestApp.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ErrorLoggingEndToEndTest {

    private static final Path LOG_DIR = Path.of("target/test-logs/error-e2e");

    static {
        // Set before Spring Boot initialises logging so logback's ${LOG_DIR} resolves here.
        System.setProperty("LOG_DIR", LOG_DIR.toString());
    }

    @LocalServerPort
    private int port;

    @Test
    void unexpectedException_returns500_andWritesStackTraceWithRequestIdToErrorLog() throws Exception {
        HttpResponse<String> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/boom")).build(),
                HttpResponse.BodyHandlers.ofString());

        // 1. Client gets a safe 500 that does not leak internals.
        assertThat(response.statusCode()).isEqualTo(500);
        assertThat(response.body()).contains("unexpected error");
        assertThat(response.body()).doesNotContain("boom-marker-42");

        // 2. error.log captured the real exception: class, message, and a stack frame.
        String errorLog = Files.readString(LOG_DIR.resolve("error.log"));
        assertThat(errorLog).contains("java.lang.IllegalStateException");
        assertThat(errorLog).contains("boom-marker-42");
        assertThat(errorLog).contains("at dev.anand.errorlogging");

        // 3. The error line carries a real requestId (RequestIdFilter reached the handler),
        //    not the "-" placeholder — this is what makes root-cause correlation possible.
        Matcher m = Pattern.compile("\\[([0-9a-f]{8})]").matcher(errorLog);
        assertThat(m.find()).as("a requestId in [........] form should be present").isTrue();
        assertThat(m.group(1)).matches("[0-9a-f]{8}");
    }

    @Configuration
    @EnableAutoConfiguration(exclude = {
            DataSourceAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class,
            FlywayAutoConfiguration.class
    })
    static class TestApp {

        // Permit-all chain so Spring Boot's default (HTTP Basic) chain backs off and /boom
        // is reachable — this test is about error logging, not auth.
        @Bean
        SecurityFilterChain permitAll(HttpSecurity http) throws Exception {
            http.csrf(csrf -> csrf.disable())
                    .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
            return http.build();
        }

        @Bean
        BoomController boomController() {
            return new BoomController();
        }

        @Bean
        GlobalExceptionHandler globalExceptionHandler() {
            return new GlobalExceptionHandler();
        }

        @Bean
        RequestIdFilter requestIdFilter() {
            return new RequestIdFilter();
        }
    }

    @RestController
    static class BoomController {
        @GetMapping("/boom")
        String boom() {
            throw new IllegalStateException("boom-marker-42");
        }
    }
}
