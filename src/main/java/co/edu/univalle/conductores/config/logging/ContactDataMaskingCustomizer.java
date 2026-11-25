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

    /** Caracteres que no son separadores de JSON, de toString() de records ni de pares clave=valor. */
    private static final String NOT_SEPARATOR = "[^\\s@\"'<>()\\[\\]{},;:=";

    /**
     * Usuario de un email seguido de "@" y, en el lookahead, el dominio sin consumirlo. Toma tiempo lineal
     * (sin ReDoS): el usuario solo empieza después de un separador o al inicio, así que {@code find()} no
     * reintenta desde cada letra, y ambos tramos son posesivos y sin grupos repetidos. Si el dominio tiene
     * extensión lo decide {@link #hasExtension(String)}.
     */
    private static final Pattern EMAIL = Pattern.compile(
            "(?<!" + NOT_SEPARATOR + "])(" + NOT_SEPARATOR + "]++)@(?=(" + NOT_SEPARATOR + "]++))");

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
            String user = matcher.group(1);
            String replacement = hasExtension(matcher.group(2)) ? user.charAt(0) + "***@" : user + "@";
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    /**
     * Indica si el dominio tiene la forma texto.ext, con al menos dos letras ASCII después de un punto,
     * como correo.com; lo que siga a la extensión, como el punto final de una frase, no importa.
     */
    private static boolean hasExtension(String domain) {
        for (int i = 1; i < domain.length() - 2; i++) {
            if (domain.charAt(i) == '.' && isAsciiLetter(domain.charAt(i + 1))
                    && isAsciiLetter(domain.charAt(i + 2))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isAsciiLetter(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
    }

    private static String maskPhones(String value) {
        return PHONE.matcher(value).replaceAll("******$1");
    }
}
