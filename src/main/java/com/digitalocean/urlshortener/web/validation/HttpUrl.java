package com.digitalocean.urlshortener.web.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Constraint(validatedBy = HttpUrlValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface HttpUrl {

  String message() default "url must be an absolute http or https URL";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};
}
