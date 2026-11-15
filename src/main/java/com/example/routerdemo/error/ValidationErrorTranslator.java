package com.example.routerdemo.error;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ElementKind;
import jakarta.validation.MessageInterpolator;
import jakarta.validation.Path;
import jakarta.validation.ValidationException;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.metadata.ConstraintDescriptor;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.context.NoSuchMessageException;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

/**
 * Turns every kind of validation failure into the same list of {@link ValidationErrorItem}, translated into the
 * request locale.
 *
 * <p>Why this is needed in WebFlux: Bean Validation translates its messages with {@code LocaleContextHolder}, which
 * WebFlux never sets, so the messages it produces are in the JVM locale. Each message is therefore translated again
 * from the original constraint violation, in the locale of the request (see {@link #localize}).
 *
 * <p>Rejected values are never included, because they may be passwords or personal data.
 */
@Component
public class ValidationErrorTranslator {

    private static final Logger log = LoggerFactory.getLogger(ValidationErrorTranslator.class);
    private static final String LIST_ELEMENT_NODE = ".<list element>";

    private final MessageSource messages;
    private final MessageTranslator translator;
    private final ValidatorFactory validatorFactory;

    public ValidationErrorTranslator(
            MessageSource messages, MessageTranslator translator, ValidatorFactory validatorFactory) {
        this.messages = messages;
        this.translator = translator;
        this.validatorFactory = validatorFactory;
    }

    /** {@code @Valid @RequestBody} failed. */
    public List<ValidationErrorItem> fromBinding(BindingResult bindingResult, Locale locale) {
        return bindingResult.getAllErrors().stream()
                .map(error -> new ValidationErrorItem(
                        error instanceof FieldError field ? field.getField() : null,
                        error.getCode(),
                        resolve(error, locale)))
                .toList();
    }

    /**
     * Built-in method validation failed: constraints placed directly on {@code @RequestParam}, {@code @PathVariable},
     * {@code @RequestHeader}, or on a {@code @Valid @RequestBody} of the same method.
     */
    public List<ValidationErrorItem> fromMethodValidation(HandlerMethodValidationException ex, Locale locale) {
        List<ValidationErrorItem> items = new ArrayList<>();
        for (ParameterValidationResult result : ex.getParameterValidationResults()) {
            if (result instanceof ParameterErrors errors) {
                for (FieldError error : errors.getFieldErrors()) {
                    items.add(new ValidationErrorItem(error.getField(), error.getCode(), resolve(error, locale)));
                }
            } else {
                String name = apiName(result.getMethodParameter());
                for (MessageSourceResolvable error : result.getResolvableErrors()) {
                    String[] codes = error.getCodes();
                    String code = codes == null || codes.length == 0 ? null : codes[codes.length - 1];
                    items.add(new ValidationErrorItem(name, code, resolve(result, error, locale)));
                }
            }
        }
        return items;
    }

    /**
     * {@code Validator.validate} or a {@code @Validated} bean failed. For method validation only the leaf of the
     * property path is shown: the full path ({@code createUser.arg0.email}) would reveal method names.
     */
    public List<ValidationErrorItem> fromViolations(Set<ConstraintViolation<?>> violations, Locale locale) {
        return violations.stream()
                .map(violation -> new ValidationErrorItem(
                        fieldOf(violation),
                        violation
                                .getConstraintDescriptor()
                                .getAnnotation()
                                .annotationType()
                                .getSimpleName(),
                        localize(violation, locale)))
                .toList();
    }

    /** Field errors raised by application code with {@link FieldValidationException}. */
    public List<ValidationErrorItem> fromFieldViolations(List<FieldViolation> violations, Locale locale) {
        return violations.stream()
                .map(violation -> {
                    String message = translator.translate(violation.messageKey(), violation.publicArgs(), locale);
                    return new ValidationErrorItem(
                            violation.field(),
                            violation.code(),
                            message != null ? message : translator.invalidValue(locale));
                })
                .toList();
    }

    /** The name the API exposes for a parameter: the annotation's explicit name, else the Java name. */
    static String apiName(MethodParameter parameter) {
        if (parameter == null) {
            return null;
        }
        String explicit = null;
        PathVariable path = parameter.getParameterAnnotation(PathVariable.class);
        RequestParam param = parameter.getParameterAnnotation(RequestParam.class);
        RequestHeader header = parameter.getParameterAnnotation(RequestHeader.class);
        if (path != null) {
            explicit = path.name();
        } else if (param != null) {
            explicit = param.name();
        } else if (header != null) {
            explicit = header.name();
        }
        return explicit != null && !explicit.isEmpty() ? explicit : parameter.getParameterName();
    }

    private String resolve(ObjectError error, Locale locale) {
        ConstraintViolation<?> violation =
                error.contains(ConstraintViolation.class) ? error.unwrap(ConstraintViolation.class) : null;
        return violation != null ? localize(violation, locale) : resolveCodes(error, locale);
    }

    private String resolve(ParameterValidationResult result, MessageSourceResolvable error, Locale locale) {
        ConstraintViolation<?> violation;
        try {
            violation = result.unwrap(error, ConstraintViolation.class);
        } catch (IllegalArgumentException notAViolation) {
            violation = null;
        }
        return violation != null ? localize(violation, locale) : resolveCodes(error, locale);
    }

    /**
     * Translates the constraint's message template (for example {@code {post.content.required}}) in the request
     * locale. The template is looked up in {@code messages*.properties} first, then in Hibernate Validator's bundled
     * messages.
     */
    private String localize(ConstraintViolation<?> violation, Locale locale) {
        try {
            return validatorFactory
                    .getMessageInterpolator()
                    .interpolate(violation.getMessageTemplate(), new ViolationContext(violation), locale);
        } catch (RuntimeException ex) {
            log.warn(
                    "Could not interpolate validation template={} locale={}",
                    violation.getMessageTemplate(),
                    locale,
                    ex);
            return translator.invalidValue(locale);
        }
    }

    /**
     * Fallback for errors that do not come from Bean Validation. Tries the codes from most to least specific: for
     * example {@code NotBlank.createPostRequest.content}, then {@code NotBlank.content}, then {@code NotBlank}.
     */
    private String resolveCodes(MessageSourceResolvable error, Locale locale) {
        try {
            return messages.getMessage(error, locale);
        } catch (NoSuchMessageException ex) {
            log.warn(
                    "Missing translation for validation codes={} locale={}",
                    error.getCodes() == null ? null : List.of(error.getCodes()),
                    locale);
            return translator.invalidValue(locale);
        }
    }

    /** {@code imageUrls[0]} for a bean, the leaf property for method validation. */
    private static String fieldOf(ConstraintViolation<?> violation) {
        boolean methodValidation = false;
        String leafProperty = null;
        for (Path.Node node : violation.getPropertyPath()) {
            if (node.getKind() == ElementKind.METHOD || node.getKind() == ElementKind.CONSTRUCTOR) {
                methodValidation = true;
            }
            if (node.getKind() == ElementKind.PROPERTY) {
                leafProperty = node.getName();
            }
        }
        if (methodValidation) {
            return leafProperty;
        }
        String path = violation.getPropertyPath().toString().replace(LIST_ELEMENT_NODE, "");
        return path.isEmpty() ? null : path;
    }

    private record ViolationContext(ConstraintViolation<?> violation) implements MessageInterpolator.Context {

        @Override
        public ConstraintDescriptor<?> getConstraintDescriptor() {
            return violation.getConstraintDescriptor();
        }

        @Override
        public Object getValidatedValue() {
            return violation.getInvalidValue();
        }

        @Override
        public <T> T unwrap(Class<T> type) {
            if (type.isInstance(this)) {
                return type.cast(this);
            }
            throw new ValidationException("Cannot unwrap to " + type);
        }
    }
}
