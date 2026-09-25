package net.concini.sbebuddy;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares an SBE enum represented by a Java enum.
 * Each wire value is declared with {@link SbeEnumValue}; Java ordinal values
 * are not used for encoding.
 *
 * <p>Unknown wire values cause decoding to fail unless one constant is marked
 * with {@link UnknownValue}. The wire null sentinel represents absence and
 * is distinct from an unknown value.</p>
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface SbeEnum {

	/**
	 * The enum name in the schema.
	 * An empty value uses the enum's simple name.
	 */
	String name() default "";

	/**
	 * The named type used to encode the enum.
	 * Exactly one of this member and {@link #primitiveType()} must be specified.
	 */
	Class<?> encodingType() default void.class;

	/**
	 * The primitive used to encode the enum.
	 * Exactly one of this member and {@link #encodingType()} must be specified.
	 */
	PrimitiveType primitiveType() default PrimitiveType.NONE;

	/**
	 * The byte offset when this declaration is an inline member of a composite,
	 * relative to the start of that composite.
	 * Zero leaves the offset unspecified for sbe-tool to calculate.
	 *
	 * <p>To position a field or a reference to this type, use
	 * {@link SbeField#offset()} or {@link SbeRef#offset()} instead.</p>
	 */
	int offset() default 0;

	/**
	 * A semantic type label for the enum.
	 * Empty means unspecified.
	 */
	String semanticType() default "";

	/**
	 * A description of the enum.
	 * Empty means unspecified.
	 */
	String description() default "";

	/**
	 * The schema version in which the enum was introduced.
	 */
	int sinceVersion() default 0;

	/**
	 * The schema version in which the enum was deprecated.
	 * Zero means unspecified. Deprecation does not remove the enum.
	 */
	int deprecated() default 0;
}
