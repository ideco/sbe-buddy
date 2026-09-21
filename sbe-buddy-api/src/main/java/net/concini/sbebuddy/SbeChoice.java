package net.concini.sbebuddy;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The {@code choice} element, on an enum constant; {@code value} is its text,
 * the bit.
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.FIELD)
public @interface SbeChoice {

	int value();

	String name() default "";

	String description() default "";

	int sinceVersion() default 0;

	int deprecated() default 0;
}
