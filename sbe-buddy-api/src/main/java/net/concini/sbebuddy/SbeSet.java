package net.concini.sbebuddy;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares an SBE bit set whose choices are Java enum constants annotated with
 * {@link SbeChoice}. A field uses {@code Set<E>}, where {@code E} is this enum,
 * and decoding creates an {@code EnumSet}.
 *
 * <p>
 * An empty set encodes zero bits. A set bit without a declared choice causes
 * decoding to fail; {@link UnknownValue} applies only to enums.
 * </p>
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface SbeSet {

	/**
	 * The set name in the schema. An empty value uses the enum's simple name.
	 */
	String name() default "";

	/**
	 * The named type used to encode the set: an {@link SbeType} of length one over
	 * one of the primitives {@link #primitiveType()} allows. Exactly one of this
	 * member and {@link #primitiveType()} must be specified.
	 */
	Class<?> encodingType() default void.class;

	/**
	 * The primitive used to encode the set: {@code uint8}, {@code uint16},
	 * {@code uint32} or {@code uint64}. Exactly one of this member and
	 * {@link #encodingType()} must be specified.
	 */
	PrimitiveType primitiveType() default PrimitiveType.NONE;

	/**
	 * The byte offset when this declaration is an inline member of a composite,
	 * relative to the start of that composite. Zero leaves the offset unspecified
	 * for sbe-tool to calculate.
	 *
	 * <p>
	 * On a top-level declaration the offset is still written to the schema, and
	 * sbe-tool applies it to every reference inside a composite that sets no offset
	 * of its own. A field is positioned by {@link SbeField#offset()}.
	 * </p>
	 */
	int offset() default 0;

	/**
	 * A semantic type label for the set. Empty means unspecified.
	 */
	String semanticType() default "";

	/**
	 * A description of the set. Empty means unspecified.
	 */
	String description() default "";

	/**
	 * The schema version in which the set was introduced.
	 */
	int sinceVersion() default 0;

	/**
	 * The schema version in which the set was deprecated. Zero means unspecified.
	 * Deprecation does not remove the set.
	 */
	int deprecated() default 0;
}
