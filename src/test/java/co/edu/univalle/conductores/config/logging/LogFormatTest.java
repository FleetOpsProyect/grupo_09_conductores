package co.edu.univalle.conductores.config.logging;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.Configuration;

/**
 * El formato del log sale de application.yaml: ECS por defecto y texto plano con LOG_FORMAT vacío (TEC_03).
 *
 * <p>Cada caso arranca una JVM nueva, como un arranque real: Logback se configura una vez por proceso y,
 * dentro de la JVM de las pruebas, el formato de un contexto anterior se queda en su LoggerContext.
 */
class LogFormatTest {

    @Test
    void defaultFormatIsEcsJson() throws Exception {
        String line = lineWith(run(null, "mensaje ecs carlos@correo.com"), "mensaje ecs");

        assertThat(line).startsWith("{").contains("\"ecs\"", "\"log\":{\"level\":\"INFO\"", "c***@correo.com")
                .doesNotContain("carlos@correo.com");
    }

    @Test
    void emptyLogFormatFallsBackToPlainText() throws Exception {
        String line = lineWith(run("", "mensaje plano"), "mensaje plano");

        assertThat(line).doesNotStartWith("{").contains("INFO", LogFormatTest.class.getSimpleName());
    }

    private static String run(String logFormat, String message) throws IOException, InterruptedException {
        Path java = Path.of(System.getProperty("java.home"), "bin", "java");
        // The classpath goes in an argument file: on Windows it does not fit in the command line.
        Path argFile = Files.createTempFile("log-format-probe", ".args");
        argFile.toFile().deleteOnExit();
        String classPath = System.getProperty("java.class.path").replace('\\', '/');
        Files.writeString(argFile, "-cp \"" + classPath + "\"", StandardCharsets.UTF_8);
        ProcessBuilder builder = new ProcessBuilder(java.toString(), "@" + argFile,
                Probe.class.getName(), message).redirectErrorStream(true);
        builder.environment().remove("LOG_FORMAT");
        if (logFormat != null) {
            builder.environment().put("LOG_FORMAT", logFormat);
        }
        Process process = builder.start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(process.waitFor(60, TimeUnit.SECONDS)).as(output).isTrue();
        assertThat(process.exitValue()).as(output).isZero();
        return output;
    }

    private static String lineWith(String output, String text) {
        return output.lines().filter(line -> line.contains(text)).findFirst()
                .orElseThrow(() -> new AssertionError("No hay una línea con: " + text + "\n" + output));
    }

    /**
     * Arranca un contexto vacío con la configuración de application.yaml y escribe un log INFO.
     */
    @Configuration(proxyBeanMethods = false)
    static class Probe {

        /**
         * Punto de entrada del proceso hijo.
         *
         * @param args mensaje a escribir en el log
         */
        public static void main(String[] args) {
            new SpringApplicationBuilder(Probe.class)
                    .web(WebApplicationType.NONE)
                    .properties("SERVER_PORT=0", "FIREBASE_PROJECT_ID=demo")
                    .run()
                    .close();
            LoggerFactory.getLogger(LogFormatTest.class).info(args[0]);
        }
    }
}
