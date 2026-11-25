package co.edu.univalle.conductores.config.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class ContactDataMaskingCustomizerTest {

    @Test
    void masksEmailKeepingFirstLetterAndDomain() {
        assertThat(ContactDataMaskingCustomizer.mask("email=carlos.perez@correo.com guardado"))
                .isEqualTo("email=c***@correo.com guardado");
    }

    @Test
    void masksEveryEmailInTheText() {
        assertThat(ContactDataMaskingCustomizer.mask("a@x.co y ana@univalle.edu.co"))
                .isEqualTo("a***@x.co y a***@univalle.edu.co");
    }

    @Test
    void masksMobilePhoneKeepingLastFourDigits() {
        assertThat(ContactDataMaskingCustomizer.mask("phone=3001234567,"))
                .isEqualTo("phone=******4567,");
        assertThat(ContactDataMaskingCustomizer.mask("\"phone\":\"3159876543\""))
                .isEqualTo("\"phone\":\"******6543\"");
    }

    @Test
    void leavesIdentifiersDocumentsAndDatesUntouched() {
        String text = "driver 7b0e4a52-2f1c-4c55-9d3e-312345678901 event ab3123456789 doc 1144056789 "
                + "otro 31234567890 corto 312345 at 2026-11-20T10:00:00-05:00";

        assertThat(ContactDataMaskingCustomizer.mask(text)).isEqualTo(text);
    }

    @Test
    void masksEmailAtTheEndOfASentence() {
        assertThat(ContactDataMaskingCustomizer.mask("Escríbale a carlos@correo.com."))
                .isEqualTo("Escríbale a c***@correo.com.");
    }

    @Test
    void masksEmailInsideJsonAndParentheses() {
        assertThat(ContactDataMaskingCustomizer.mask("{\"email\":\"ana@univalle.edu.co\"} (b@x.com)"))
                .isEqualTo("{\"email\":\"a***@univalle.edu.co\"} (b***@x.com)");
    }

    @Test
    void masksLongInputsInLinearTime() {
        String longUser = "x".repeat(50_000) + "@";
        String longDomain = "a@" + "b.".repeat(25_000) + "1";

        assertTimeoutPreemptively(Duration.ofSeconds(1), () -> {
            assertThat(ContactDataMaskingCustomizer.mask(longUser)).isEqualTo(longUser);
            assertThat(ContactDataMaskingCustomizer.mask(longDomain)).isEqualTo(longDomain);
        });
    }

    @Test
    void handlesNullAndEmpty() {
        assertThat(ContactDataMaskingCustomizer.mask(null)).isNull();
        assertThat(ContactDataMaskingCustomizer.mask("")).isEmpty();
    }
}
