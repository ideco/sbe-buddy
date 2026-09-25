package net.concini.sbebuddy;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a bit position for a constant in an {@link SbeSet} enum. A set
 * containing that constant sets the corresponding bit on the wire.
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.FIELD)
public @interface SbeChoice {

	/**
	 * The zero-based bit position, not a bit mask. It must fit within the set's
	 * encoding type and be unique within the set; sbe-tool checks both.
	 */
	int value();

	/**
	 * The choice name in the schema. An empty value uses the Java enum constant's
	 * name.
	 */
	String name() default "";

	/**
	 * A description of the choice. Empty means unspecified.
	 */
	String description() default "";

	/**
	 * The schema version in which the choice was introduced.
	 */
	int sinceVersion() default 0;

	/**
	 * The schema version in which the choice was deprecated. Zero means
	 * unspecified. Deprecation does not remove the choice.
	 */
	int deprecated() default 0;
}
