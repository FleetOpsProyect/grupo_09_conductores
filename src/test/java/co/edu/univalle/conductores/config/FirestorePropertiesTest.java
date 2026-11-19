package co.edu.univalle.conductores.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

/**
 * Binding and startup validation of {@code app.firestore.timeout} ({@code FIRESTORE_TIMEOUT}).
 *
 * <p>The context reads the real {@code application.yaml}, without the machine's environment
 * variables, so {@code FIRESTORE_TIMEOUT} is controlled only through system properties.
 */
class FirestorePropertiesTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(FirestorePropertiesTest::useApplicationYamlWithoutEnvironmentVariables)
            .withUserConfiguration(PropertiesConfiguration.class);

    @Test
    void defaultsToTwoSecondsWhenTheVariableIsNotSet() {
        runner.run(context -> assertThat(context.getBean(FirestoreProperties.class).timeout())
                .isEqualTo(Duration.ofSeconds(2)));
    }

    @Test
    void usesTheConfiguredValue() {
        runner.withSystemProperties("FIRESTORE_TIMEOUT=750ms")
                .run(context -> assertThat(context.getBean(FirestoreProperties.class).timeout())
                        .isEqualTo(Duration.ofMillis(750)));
    }

    @Test
    void startsWithTheMinimumOfOneMillisecond() {
        runner.withSystemProperties("FIRESTORE_TIMEOUT=1ms")
                .run(context -> assertThat(context.getBean(FirestoreProperties.class).timeout())
                        .isEqualTo(Duration.ofMillis(1)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0s", "-1s", "1ns", "500us"})
    void doesNotStartWithAValueBelowOneMillisecond(String value) {
        runner.withSystemProperties("FIRESTORE_TIMEOUT=" + value).run(FirestorePropertiesTest::assertRejected);
    }

    /**
     * {@code withSystemProperties} removes a property whose value is empty, so an empty variable
     * ({@code FIRESTORE_TIMEOUT=}) is added as its own property source instead.
     */
    @Test
    void doesNotStartWithAnEmptyValue() {
        runner.withInitializer(context -> context.getEnvironment().getPropertySources()
                        .addFirst(new MapPropertySource("emptyVariable", Map.of("FIRESTORE_TIMEOUT", ""))))
                .run(FirestorePropertiesTest::assertRejected);
    }

    private static void assertRejected(AssertableApplicationContext context) {
        assertThat(context).hasFailed();
        assertThat(context.getStartupFailure()).rootCause()
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("app.firestore.timeout (FIRESTORE_TIMEOUT) must be at least 1 ms");
    }

    /**
     * Replaces the machine's environment variables with the real {@code application.yaml}.
     *
     * @param context context being prepared
     */
    private static void useApplicationYamlWithoutEnvironmentVariables(ConfigurableApplicationContext context) {
        MutablePropertySources sources = context.getEnvironment().getPropertySources();
        sources.remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        try {
            sources.addLast(new YamlPropertySourceLoader()
                    .load("application.yaml", new ClassPathResource("application.yaml")).get(0));
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    /** Registers only the properties under test, without a Firestore client. */
    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(FirestoreProperties.class)
    static class PropertiesConfiguration {
    }
}
