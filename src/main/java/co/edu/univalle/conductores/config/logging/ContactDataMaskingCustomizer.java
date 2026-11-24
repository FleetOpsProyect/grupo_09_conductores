package co.edu.univalle.conductores.config.logging;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.boot.json.JsonWriter;
import org.springframework.boot.logging.structured.StructuredLoggingJsonMembersCustomizer;

/**
 * Enmascara los datos de contacto en todos los campos de texto del log JSON (TEC_03).
 *
 * <p>Se aplica al mensaje, a la traza de la excepción y al contexto, así que ningún log puede sacar un
 * email o un teléfono completos aunque el código los incluya por error. Un email queda con la primera
 * letra del usuario y el dominio ({@code c***@correo.com}); un teléfono móvil colombiano de 10 dígitos
 * que empieza por 3 queda con sus últimos 4 dígitos ({@code ******4567}).
 *
 * <p>Se registra en {@code logging.structured.json.customizer}; Spring Boot la instancia antes del
 * contexto, por eso no es un bean.
 */
public class ContactDataMaskingCustomizer implements StructuredLoggingJsonMembersCustomizer<Object> {

    /** Sin separadores de JSON, de toString() de records ni de pares clave=valor alrededor. */
    private static final Pattern EMAIL = Pattern.compile(
            "([^\\s@\"'<>()\\[\\]{},;:=]+)@([^\\s@\"'<>()\\[\\]{},;:=]+\\.[A-Za-z]{2,})");

    /** Sin letras ni dígitos alrededor, para no tocar UUIDs, números de documento ni fechas. */
    private static final Pattern PHONE = Pattern.compile("(?<![0-9A-Za-z])3\\d{5}(\\d{4})(?![0-9A-Za-z])");

    @Override
    public void customize(JsonWriter.Members<Object> members) {
        members.applyingValueProcessor(JsonWriter.ValueProcessor.of(String.class, ContactDataMaskingCustomizer::mask));
    }

    /**
     * Enmascara los emails y teléfonos que aparezcan en un texto.
     *
     * @param value texto original, puede ser null
     * @return texto sin datos de contacto completos
     */
    public static String mask(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        return maskPhones(maskEmails(value));
    }

    private static String maskEmails(String value) {
        if (value.indexOf('@') < 0) {
            return value;
        }
        Matcher matcher = EMAIL.matcher(value);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String masked = matcher.group(1).charAt(0) + "***@" + matcher.group(2);
            matcher.appendReplacement(result, Matcher.quoteReplacement(masked));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private static String maskPhones(String value) {
        return PHONE.matcher(value).replaceAll("******$1");
    }
}
