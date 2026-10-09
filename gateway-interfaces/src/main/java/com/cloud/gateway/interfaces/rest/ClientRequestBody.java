package com.cloud.gateway.interfaces.rest;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Body binding that injects trusted Client/session context before validation. */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface ClientRequestBody {
    boolean required() default true;
}
