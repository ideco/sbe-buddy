package net.concini.sbebuddy;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The {@code validValue} element, on an enum constant; {@code value} is its
 * text.
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.FIELD)
public @interface SbeEnumValue {

	String value();

	String name() default "";

	String description() default "";

	int sinceVersion() default 0;

	int deprecated() default 0;
}
