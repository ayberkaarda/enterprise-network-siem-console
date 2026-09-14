package com.example.demo.common;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.regex.Pattern;

/**
 * Single source of truth for the IPv4 address rule. Both the bean validation
 * path (request DTOs) and the service layer entry points that accept a fully
 * built entity delegate here, so the rule cannot drift between them.
 */
public class IpAddressValidator implements ConstraintValidator<ValidIpAddress, String> {

    public static final String IPV4_PATTERN =
            "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$";

    private static final Pattern COMPILED_IPV4_PATTERN = Pattern.compile(IPV4_PATTERN);

    /**
     * @return {@code true} only for a non-null dotted-quad IPv4 address.
     */
    public static boolean isValidIpv4(String value) {
        return value != null && COMPILED_IPV4_PATTERN.matcher(value).matches();
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return isValidIpv4(value);
    }
}
