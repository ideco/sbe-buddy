package net.concini.sbebuddy;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares an SBE composite represented by a Java record. Its components
 * describe inline types, references or nested declarations, ordered by
 * component declaration unless {@link #layout()} specifies otherwise.
 *
 * <p>
 * Use {@link SbeType} for an inline primitive type and {@link SbeRef} for a
 * reference to a named type. An enum, set or composite declared inside the
 * record can supply an inline declaration through a component of that type.
 * </p>
 *
 * <p>
 * Ordinary composites cannot gain members in later schema versions: their
 * flyweights do not skip members based on the acting version. To add members,
 * declare a new composite and a new field using it. Message headers have
 * separate layout rules.
 * </p>
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface SbeComposite {

	/**
	 * The composite name in the schema. An empty value uses the record's simple
	 * name.
	 */
	String name() default "";

	/**
	 * The byte offset when this declaration is an inline member of a composite,
	 * relative to the start of that composite. Zero leaves the offset unspecified
	 * for sbe-tool to calculate.
	 *
	 * <p>
	 * To position a field or a reference to this type, use
	 * {@link SbeField#offset()} or {@link SbeRef#offset()} instead.
	 * </p>
	 */
	int offset() default 0;

	/**
	 * A semantic type label for the composite. Empty means unspecified.
	 */
	String semanticType() default "";

	/**
	 * A description of the composite. Empty means unspecified.
	 */
	String description() default "";

	/**
	 * The schema version in which the composite was introduced.
	 */
	int sinceVersion() default 0;

	/**
	 * The schema version in which the composite was deprecated. Zero means
	 * unspecified. Deprecation does not remove the composite.
	 */
	int deprecated() default 0;

	/**
	 * The wire order of record components and {@linkplain #unmapped() unmapped
	 * members}. Names refer to Java record components or the declared names of
	 * unmapped members.
	 *
	 * <p>
	 * When specified, every component and unmapped member must appear exactly once.
	 * When empty, components follow their declaration order. This does not change
	 * the record's constructor order.
	 * </p>
	 */
	String[] layout() default {};

	/**
	 * Inline type members declared without corresponding record components. Each
	 * must specify its name and primitive type, and appear in {@link #layout()}.
	 *
	 * <p>
	 * Encoding writes the member's null or empty representation; decoding skips it.
	 * This preserves its place on the wire after the component is removed.
	 * </p>
	 */
	SbeType[] unmapped() default {};
}
