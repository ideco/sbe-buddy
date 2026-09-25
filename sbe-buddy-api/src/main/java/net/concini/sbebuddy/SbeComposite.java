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
 * reference to a named type. An enum or composite nested directly in the record
 * supplies an inline declaration through a component of its type, and a set
 * through a component of {@code Set<E>}.
 * </p>
 *
 * <p>
 * A composite cannot gain members in later schema versions: no member may have
 * a {@code sinceVersion} above the composite's, because its flyweights do not
 * skip members by the acting version. To add members, declare a new composite
 * and a new field using it. A message header cannot change at all: neither it
 * nor a member has a {@code sinceVersion}.
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
	 * On a top-level declaration the offset is still written to the schema, and
	 * sbe-tool applies it to every reference inside a composite that sets no offset
	 * of its own. A field is positioned by {@link SbeField#offset()}.
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
	 * Encoding writes the member's null value, in every element of an array;
	 * decoding skips it. This preserves its place on the wire after the component
	 * is removed.
	 * </p>
	 */
	SbeType[] unmapped() default {};
}
