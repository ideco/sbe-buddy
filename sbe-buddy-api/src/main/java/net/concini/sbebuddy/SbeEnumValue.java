package net.concini.sbebuddy;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares the wire value of a constant in an {@link SbeEnum} enum.
 * The schema value is explicit and independent of the Java constant's ordinal.
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.FIELD)
public @interface SbeEnumValue {

	/**
	 * The value written as text in the schema, such as {@code "1"} for an integer
	 * encoding or {@code "A"} for a character encoding.
	 * It must be valid for the enum's encoding type.
	 */
	String value();

	/**
	 * The enum value name in the schema.
	 * An empty value uses the Java enum constant's name.
	 */
	String name() default "";

	/**
	 * A description of the enum value.
	 * Empty means unspecified.
	 */
	String description() default "";

	/**
	 * The schema version in which the enum value was introduced.
	 */
	int sinceVersion() default 0;

	/**
	 * The schema version in which the enum value was deprecated.
	 * Zero means unspecified. Deprecation does not remove the enum value.
	 */
	int deprecated() default 0;
}
