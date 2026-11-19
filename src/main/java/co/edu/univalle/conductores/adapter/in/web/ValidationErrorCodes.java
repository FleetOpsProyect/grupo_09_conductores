package co.edu.univalle.conductores.adapter.in.web;

import co.edu.univalle.conductores.adapter.in.web.dto.ErrorResponse.FieldError;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Maps validation failures to the per-field codes of the API contract (errors[].code).
 */
final class ValidationErrorCodes {

    static final String REQUIRED = "REQUIRED";
    static final String INVALID_TYPE = "INVALID_TYPE";
    static final String INVALID_LENGTH = "INVALID_LENGTH";
    static final String INVALID_FORMAT = "INVALID_FORMAT";
    static final String VALUE_NOT_ALLOWED = "VALUE_NOT_ALLOWED";

    /** When a field fails several rules, the code listed first wins. */
    private static final List<String> PRIORITY =
            List.of(REQUIRED, INVALID_TYPE, INVALID_LENGTH, INVALID_FORMAT, VALUE_NOT_ALLOWED);

    private static final Map<String, String> BY_CONSTRAINT = Map.ofEntries(
            Map.entry("NotNull", REQUIRED),
            Map.entry("NotBlank", REQUIRED),
            Map.entry("NotEmpty", REQUIRED),
            Map.entry("Size", INVALID_LENGTH),
            Map.entry("Length", INVALID_LENGTH),
            Map.entry("Pattern", INVALID_FORMAT),
            Map.entry("Email", INVALID_FORMAT),
            Map.entry("Min", VALUE_NOT_ALLOWED),
            Map.entry("Max", VALUE_NOT_ALLOWED),
            Map.entry("DecimalMin", VALUE_NOT_ALLOWED),
            Map.entry("DecimalMax", VALUE_NOT_ALLOWED),
            Map.entry("Positive", VALUE_NOT_ALLOWED),
            Map.entry("PositiveOrZero", VALUE_NOT_ALLOWED),
            Map.entry("Negative", VALUE_NOT_ALLOWED),
            Map.entry("NegativeOrZero", VALUE_NOT_ALLOWED),
            Map.entry("typeMismatch", INVALID_TYPE));

    private ValidationErrorCodes() {
    }

    /**
     * Code for a constraint, given its simple annotation name (for example "NotBlank").
     * Unknown constraints fall back to INVALID_FORMAT.
     */
    static String fromConstraint(String constraint) {
        return constraint == null ? INVALID_FORMAT : BY_CONSTRAINT.getOrDefault(constraint, INVALID_FORMAT);
    }

    /**
     * Code for a list of message codes, where the last one is the simple constraint name
     * (for example ["Max.list.size", "Max.size", "Max.int", "Max"]).
     */
    static String fromMessageCodes(String[] codes) {
        return codes == null || codes.length == 0 ? INVALID_FORMAT : fromConstraint(codes[codes.length - 1]);
    }

    /**
     * Code for a value that could not be converted to the expected type.
     * Enums are a closed set of values, dates and UUIDs have a fixed format, anything else is a type error.
     */
    static String fromTargetType(Class<?> type) {
        if (type == null) {
            return INVALID_TYPE;
        }
        if (type.isEnum()) {
            return VALUE_NOT_ALLOWED;
        }
        if (UUID.class.equals(type) || "java.time".equals(type.getPackageName())) {
            return INVALID_FORMAT;
        }
        return INVALID_TYPE;
    }

    /**
     * Keeps one entry per field, choosing the code with the highest priority, in first-seen field order.
     */
    static List<FieldError> onePerField(List<FieldError> errors) {
        Map<String, FieldError> byField = new LinkedHashMap<>();
        for (FieldError error : errors) {
            byField.merge(error.field(), error,
                    (current, candidate) -> rank(candidate.code()) < rank(current.code()) ? candidate : current);
        }
        return new ArrayList<>(byField.values());
    }

    private static int rank(String code) {
        int index = PRIORITY.indexOf(code);
        return index < 0 ? PRIORITY.size() : index;
    }
}
