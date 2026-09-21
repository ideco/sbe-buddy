package net.concini.sbebuddy;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The {@code message} element, on a record whose components are its fields,
 * then groups, then data.
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface SbeMessage {

	int id();

	String name() default "";

	int blockLength() default 0;

	String semanticType() default "";

	String description() default "";

	int sinceVersion() default 0;

	int deprecated() default 0;
}
