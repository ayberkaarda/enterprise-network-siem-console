package com.example.demo.common;

/**
 * Raised when a correlation rule's {@code conditionJson} is rejected at write
 * time: the text is not syntactically valid JSON, its {@code type}
 * discriminator is missing, or that discriminator is not one the correlation
 * engine knows how to evaluate. It does not check the fields a given type
 * expects beyond that; those stay the correlation engine's concern at
 * evaluation time, not the API's concern at write time.
 */
public class InvalidRuleConditionException extends RuntimeException {

    public InvalidRuleConditionException(String message) {
        super(message);
    }
}
