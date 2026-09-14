package com.example.demo.common;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares that the annotated field must hold a dotted-quad IPv4 address.
 * A {@code null} value is rejected as well: an unset address is never usable.
 */
@Documented
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = IpAddressValidator.class)
public @interface ValidIpAddress {

    String message() default "Verilen adres kurumsal IPv4 standartlarına uymuyor!";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
