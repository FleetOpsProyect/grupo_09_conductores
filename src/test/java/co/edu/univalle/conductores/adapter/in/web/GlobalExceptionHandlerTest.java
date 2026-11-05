package co.edu.univalle.conductores.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import co.edu.univalle.conductores.adapter.in.web.dto.ErrorResponse;
import co.edu.univalle.conductores.adapter.in.web.dto.ErrorResponse.FieldError;
import co.edu.univalle.conductores.domain.DriverStatus;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.DirectFieldBindingResult;
import org.springframework.validation.beanvalidation.SpringValidatorAdapter;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

class GlobalExceptionHandlerTest {

    private static final JsonMapper MAPPER = JsonMapper.builder()
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .build();

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler(new JsonFieldNameResolver(MAPPER));
    private final MockHttpServletRequest request = new MockHttpServletRequest("POST", "/drivers");

    /** Request with the rules that later stories use for valid_until and notes. */
    record SampleRequest(
            @NotBlank @Size(min = 3, max = 100) String fullName,
            @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$") String validUntil,
            @Size(max = 500) String notes) {
    }

    record TypedRequest(LocalDate validUntil, int count) {
    }

    @BeforeAll
    static void createValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        validatorFactory.close();
    }

    @SuppressWarnings("unused")
    private void endpoint(SampleRequest body, int page, DriverStatus status) {
        // Only used to build MethodParameter instances.
    }

    @Test
    void validUntilWithoutIsoFormatIsInvalidFormat() throws Exception {
        ResponseEntity<ErrorResponse> response = handler.handleInvalidBody(
                invalidBody(new SampleRequest("Carlos", "05/11/2026", null)), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().code()).isEqualTo("INVALID_DATA");
        assertThat(response.getBody().errors()).containsExactly(new FieldError("valid_until", "INVALID_FORMAT"));
    }

    @Test
    void notesLongerThan500IsInvalidLength() throws Exception {
        ResponseEntity<ErrorResponse> response = handler.handleInvalidBody(
                invalidBody(new SampleRequest("Carlos", "2026-11-05", "x".repeat(501))), request);

        assertThat(response.getBody().code()).isEqualTo("INVALID_DATA");
        assertThat(response.getBody().errors()).containsExactly(new FieldError("notes", "INVALID_LENGTH"));
    }

    @Test
    void blankRequiredFieldIsReportedOnceAsRequired() throws Exception {
        ResponseEntity<ErrorResponse> response = handler.handleInvalidBody(
                invalidBody(new SampleRequest("", "2026-11-05", null)), request);

        assertThat(response.getBody().errors()).containsExactly(new FieldError("full_name", "REQUIRED"));
    }

    @Test
    void malformedJsonIsInvalidJson() {
        ResponseEntity<ErrorResponse> response = handler.handleUnreadableBody(
                unreadable("{\"full_name\":"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().code()).isEqualTo("INVALID_JSON");
        assertThat(response.getBody().errors()).isEmpty();
    }

    @Test
    void wrongRootTypeIsInvalidJson() {
        ResponseEntity<ErrorResponse> response = handler.handleUnreadableBody(unreadable("[]"), request);

        assertThat(response.getBody().code()).isEqualTo("INVALID_JSON");
    }

    @Test
    void unreadableBodyWithoutCauseIsInvalidJson() {
        HttpMessageNotReadableException ex = new HttpMessageNotReadableException("Required request body is missing",
                new MockHttpInputMessage(new byte[0]));

        assertThat(handler.handleUnreadableBody(ex, request).getBody().code()).isEqualTo("INVALID_JSON");
    }

    @Test
    void dateWithWrongFormatInBodyIsInvalidFormat() {
        ResponseEntity<ErrorResponse> response = handler.handleUnreadableBody(
                unreadable("{\"valid_until\":\"05/11/2026\"}"), request);

        assertThat(response.getBody().code()).isEqualTo("INVALID_DATA");
        assertThat(response.getBody().errors()).containsExactly(new FieldError("valid_until", "INVALID_FORMAT"));
    }

    @Test
    void numberWithWrongTypeInBodyIsInvalidType() {
        ResponseEntity<ErrorResponse> response = handler.handleUnreadableBody(
                unreadable("{\"count\":\"abc\"}"), request);

        assertThat(response.getBody().code()).isEqualTo("INVALID_DATA");
        assertThat(response.getBody().errors()).containsExactly(new FieldError("count", "INVALID_TYPE"));
    }

    @Test
    void unknownEnumParameterIsValueNotAllowed() throws Exception {
        ResponseEntity<ErrorResponse> response = handler.handleTypeMismatch(
                typeMismatch("FOO", DriverStatus.class, "status", 2), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().code()).isEqualTo("INVALID_DATA");
        assertThat(response.getBody().errors()).containsExactly(new FieldError("status", "VALUE_NOT_ALLOWED"));
    }

    @Test
    void nonNumericParameterIsInvalidType() throws Exception {
        ResponseEntity<ErrorResponse> response = handler.handleTypeMismatch(
                typeMismatch("abc", int.class, "page", 1), request);

        assertThat(response.getBody().errors()).containsExactly(new FieldError("page", "INVALID_TYPE"));
    }

    private MethodArgumentNotValidException invalidBody(SampleRequest body) throws Exception {
        DirectFieldBindingResult result = new DirectFieldBindingResult(body, "sampleRequest");
        new SpringValidatorAdapter(validator).validate(body, result);
        return new MethodArgumentNotValidException(parameter(0), result);
    }

    private static HttpMessageNotReadableException unreadable(String json) {
        JacksonException cause = catchThrowableOfType(JacksonException.class,
                () -> MAPPER.readValue(json, TypedRequest.class));
        return new HttpMessageNotReadableException(cause.getOriginalMessage(), cause,
                new MockHttpInputMessage(json.getBytes()));
    }

    private static MethodArgumentTypeMismatchException typeMismatch(String value, Class<?> type, String name,
            int index) throws Exception {
        return new MethodArgumentTypeMismatchException(value, type, name, parameter(index),
                new IllegalArgumentException("conversion failed"));
    }

    private static MethodParameter parameter(int index) throws NoSuchMethodException {
        return new MethodParameter(GlobalExceptionHandlerTest.class.getDeclaredMethod("endpoint",
                SampleRequest.class, int.class, DriverStatus.class), index);
    }
}
