package com.truesight.auth;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The rules for a password someone chooses: not blank, and between 8 and 72 characters (BCrypt reads at most 72). Sign
 * Up and Change Password both use this one annotation, so the two can never disagree.
 *
 * <p>It only combines the two rules below; each reports its own message, such as "must be between 8 and 72 characters".
 */
@NotBlank
@Size(min = 8, max = 72, message = "must be between 8 and 72 characters")
@Documented
@Constraint(validatedBy = {})
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface NewPassword {

    String message() default "";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
