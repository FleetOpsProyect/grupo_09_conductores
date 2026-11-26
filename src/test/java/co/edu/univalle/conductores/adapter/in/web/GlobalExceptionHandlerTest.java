package co.edu.univalle.conductores.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import co.edu.univalle.conductores.adapter.in.web.dto.ErrorResponse;
import co.edu.univalle.conductores.adapter.in.web.dto.ErrorResponse.FieldError;
import co.edu.univalle.conductores.domain.DocumentationUnavailableException;
import co.edu.univalle.conductores.domain.DriverStatus;
import co.edu.univalle.conductores.domain.FieldNotEditableException;
import co.edu.univalle.conductores.domain.PersistenceUnavailableException;
import co.edu.univalle.conductores.domain.StatusNotAllowedException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.DirectFieldBindingResult;
import org.springframework.validation.beanvalidation.SpringValidatorAdapter;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.ServletRequestBindingException;
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
    void fieldNotEditableIsInvalidDataWithNotEditableCode() {
        ResponseEntity<ErrorResponse> response = handler.handleFieldNotEditable(
                new FieldNotEditableException("document_number"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().code()).isEqualTo("INVALID_DATA");
        assertThat(response.getBody().errors()).containsExactly(new FieldError("document_number", "NOT_EDITABLE"));
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

    @Test
    void unsupportedMediaTypeIs415WithAcceptHeader() {
        ResponseEntity<ErrorResponse> response = handler.handleUnsupportedMediaType(
                new HttpMediaTypeNotSupportedException(MediaType.TEXT_PLAIN, List.of(MediaType.APPLICATION_JSON)),
                request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
        assertThat(response.getHeaders().getAccept()).containsExactly(MediaType.APPLICATION_JSON);
        assertThat(response.getBody().code()).isEqualTo("UNSUPPORTED_MEDIA_TYPE");
        assertThat(response.getBody().path()).isEqualTo("/drivers");
        assertThat(response.getBody().errors()).isEmpty();
    }

    @Test
    void notAcceptableIs406WithJsonBody() {
        ResponseEntity<ErrorResponse> response = handler.handleNotAcceptable(
                new HttpMediaTypeNotAcceptableException(List.of(MediaType.APPLICATION_JSON)), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_ACCEPTABLE);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
        assertThat(response.getBody().code()).isEqualTo("NOT_ACCEPTABLE");
    }

    @Test
    void otherSpringMvcExceptionKeepsItsStatusInTheCommonFormat() {
        ResponseEntity<ErrorResponse> response = handler.handleUnexpected(
                new ServletRequestBindingException("Missing header X-Secret"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().code()).isEqualTo("REQUEST_NOT_PROCESSABLE");
        assertThat(response.getBody().message()).isEqualTo("La solicitud no se pudo procesar");
        assertThat(response.getBody().path()).isEqualTo("/drivers");
    }

    @Test
    void unexpectedExceptionIsInternalErrorWithoutDetails() {
        ResponseEntity<ErrorResponse> response = handler.handleUnexpected(
                new IllegalStateException("secret detail"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().code()).isEqualTo("INTERNAL_ERROR");
        assertThat(response.getBody().message()).isEqualTo("Error interno del servicio");
    }

    @Test
    void persistenceUnavailableIsServiceUnavailableWithFixedMessage() {
        ResponseEntity<ErrorResponse> response = handler.handlePersistenceUnavailable(
                new PersistenceUnavailableException("secret detail", new IllegalStateException("cause")), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody().code()).isEqualTo("SERVICE_UNAVAILABLE");
        assertThat(response.getBody().message()).isEqualTo("Servicio temporalmente no disponible, intente nuevamente");
        assertThat(response.getBody().path()).isEqualTo("/drivers");
        assertThat(response.getBody().errors()).isEmpty();
    }

    @Test
    void statusNotAllowedReturnsConflict() {
        StatusNotAllowedException ex = new StatusNotAllowedException("El conductor no está inhabilitado por incidente");
        ResponseEntity<ErrorResponse> response = handler.handleStatusNotAllowed(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().code()).isEqualTo("STATUS_NOT_ALLOWED");
        assertThat(response.getBody().message()).isEqualTo("El conductor no está inhabilitado por incidente");
        assertThat(response.getBody().errors()).isEmpty();
    }

    @Test
    void documentationUnavailableReturns503DependencyUnavailable() {
        DocumentationUnavailableException ex = new DocumentationUnavailableException("timeout", new RuntimeException());
        ResponseEntity<ErrorResponse> response = handler.handleDocumentationUnavailable(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody().code()).isEqualTo("DEPENDENCY_UNAVAILABLE");
        assertThat(response.getBody().message())
                .isEqualTo("No fue posible validar la vigencia de las licencias, intente nuevamente");
        assertThat(response.getBody().path()).isEqualTo("/drivers");
        assertThat(response.getBody().errors()).isEmpty();
    }

    @Test
    void methodNotAllowedWithoutSupportedMethodsHasNoAllowHeader() {
        ResponseEntity<ErrorResponse> response = handler.handleMethodNotAllowed(
                new HttpRequestMethodNotSupportedException("DELETE"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(response.getBody().code()).isEqualTo("METHOD_NOT_ALLOWED");
        assertThat(response.getHeaders().containsHeader(HttpHeaders.ALLOW)).isFalse();
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
