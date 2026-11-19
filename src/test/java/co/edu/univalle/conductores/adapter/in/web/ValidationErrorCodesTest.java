package co.edu.univalle.conductores.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import co.edu.univalle.conductores.adapter.in.web.dto.ErrorResponse.FieldError;
import co.edu.univalle.conductores.domain.DriverStatus;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ValidationErrorCodesTest {

    @ParameterizedTest
    @CsvSource({
        "NotNull, REQUIRED",
        "NotBlank, REQUIRED",
        "NotEmpty, REQUIRED",
        "Size, INVALID_LENGTH",
        "Length, INVALID_LENGTH",
        "Pattern, INVALID_FORMAT",
        "Email, INVALID_FORMAT",
        "Min, VALUE_NOT_ALLOWED",
        "Max, VALUE_NOT_ALLOWED",
        "DecimalMin, VALUE_NOT_ALLOWED",
        "DecimalMax, VALUE_NOT_ALLOWED",
        "Positive, VALUE_NOT_ALLOWED",
        "PositiveOrZero, VALUE_NOT_ALLOWED",
        "Negative, VALUE_NOT_ALLOWED",
        "NegativeOrZero, VALUE_NOT_ALLOWED",
        "typeMismatch, INVALID_TYPE",
        "Future, INVALID_FORMAT"
    })
    void mapsConstraintToCode(String constraint, String expected) {
        assertThat(ValidationErrorCodes.fromConstraint(constraint)).isEqualTo(expected);
    }

    @Test
    void nullConstraintFallsBackToInvalidFormat() {
        assertThat(ValidationErrorCodes.fromConstraint(null)).isEqualTo("INVALID_FORMAT");
    }

    @Test
    void usesLastMessageCodeAsConstraintName() {
        String[] codes = {"Max.driverController#list.size", "Max.size", "Max.int", "Max"};

        assertThat(ValidationErrorCodes.fromMessageCodes(codes)).isEqualTo("VALUE_NOT_ALLOWED");
    }

    @Test
    void missingMessageCodesFallBackToInvalidFormat() {
        assertThat(ValidationErrorCodes.fromMessageCodes(null)).isEqualTo("INVALID_FORMAT");
        assertThat(ValidationErrorCodes.fromMessageCodes(new String[0])).isEqualTo("INVALID_FORMAT");
    }

    @Test
    void mapsTargetTypeToCode() {
        assertThat(ValidationErrorCodes.fromTargetType(DriverStatus.class)).isEqualTo("VALUE_NOT_ALLOWED");
        assertThat(ValidationErrorCodes.fromTargetType(UUID.class)).isEqualTo("INVALID_FORMAT");
        assertThat(ValidationErrorCodes.fromTargetType(LocalDate.class)).isEqualTo("INVALID_FORMAT");
        assertThat(ValidationErrorCodes.fromTargetType(OffsetDateTime.class)).isEqualTo("INVALID_FORMAT");
        assertThat(ValidationErrorCodes.fromTargetType(int.class)).isEqualTo("INVALID_TYPE");
        assertThat(ValidationErrorCodes.fromTargetType(String.class)).isEqualTo("INVALID_TYPE");
        assertThat(ValidationErrorCodes.fromTargetType(null)).isEqualTo("INVALID_TYPE");
    }

    @Test
    void keepsOneEntryPerFieldByPriority() {
        List<FieldError> errors = List.of(
                new FieldError("full_name", "INVALID_LENGTH"),
                new FieldError("email", "INVALID_FORMAT"),
                new FieldError("full_name", "REQUIRED"),
                new FieldError("email", "VALUE_NOT_ALLOWED"),
                new FieldError("phone", "SOMETHING_ELSE"),
                new FieldError("phone", "INVALID_FORMAT"));

        assertThat(ValidationErrorCodes.onePerField(errors)).containsExactly(
                new FieldError("full_name", "REQUIRED"),
                new FieldError("email", "INVALID_FORMAT"),
                new FieldError("phone", "INVALID_FORMAT"));
    }
}
