package net.concini.sbebuddy;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a repeating group in a message or another group. Without a binding,
 * the annotated component must be a {@code List<E>}, where {@code E} is a
 * record describing one entry.
 *
 * <p>
 * The encoded group starts with a dimension header containing the entry block
 * length and entry count. Each entry contains a fixed-length block of fields,
 * followed by nested groups and variable-length data.
 * </p>
 *
 * <p>
 * An empty list encodes a group with zero entries. A null list cannot be
 * encoded; null on decoding means the group was absent in an older schema
 * version.
 * </p>
 *
 * <p>
 * In schema-first mode, the complete group declaration must match the XML
 * schema.
 * </p>
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.RECORD_COMPONENT)
public @interface SbeGroup {

	/**
	 * The group's ID in its containing message or group.
	 */
	int id();

	/**
	 * The group name in the schema. An empty value uses the record component's
	 * name.
	 */
	String name() default "";

	/**
	 * The composite type describing the group's dimension header. Defaults to
	 * {@link GroupSizeEncoding}, which represents the entry block length and entry
	 * count as two unsigned 16-bit values. Another composite needs members named
	 * {@code blockLength} and {@code numInGroup}, each {@code uint8} or
	 * {@code uint16}, or sbe-tool refuses it.
	 */
	Class<?> dimensionType() default GroupSizeEncoding.class;

	/**
	 * The length in bytes of each entry's fixed-length block, excluding the group
	 * header, nested groups and variable-length data.
	 *
	 * <p>
	 * Zero leaves the length unspecified for sbe-tool to calculate from the field
	 * layout. A length larger than the fields require reserves trailing space in
	 * each entry for alignment or future fields; sbe-tool rejects one smaller than
	 * the fields need.
	 * </p>
	 *
	 * <p>
	 * In schema-first mode, an explicit block length in the XML must also be
	 * declared here.
	 * </p>
	 */
	int blockLength() default 0;

	/**
	 * A semantic type label for the group. Empty means unspecified.
	 */
	String semanticType() default "";

	/**
	 * A description of the group. Empty means unspecified.
	 */
	String description() default "";

	/**
	 * The schema version in which the group was introduced.
	 *
	 * <p>
	 * When decoding an older version, the group decodes to null. This differs from
	 * a group present with zero entries, which decodes to an empty list. If a
	 * binding is configured, absence bypasses it.
	 * </p>
	 *
	 * <p>
	 * Fields introduced with the group are available whenever an entry exists.
	 * Required fields introduced at that version can therefore use primitive
	 * components, even when the codec accepts older messages without the group.
	 * </p>
	 */
	int sinceVersion() default 0;

	/**
	 * The schema version in which the group was deprecated. Zero means unspecified.
	 * Deprecation does not remove the group.
	 */
	int deprecated() default 0;

	/**
	 * The wire order of entry record components and {@linkplain #unmapped()
	 * unmapped fields}. Names refer to Java record components or to the declared
	 * names of unmapped fields.
	 *
	 * <p>
	 * When specified, every component and unmapped field must appear exactly once.
	 * When empty, components follow their declaration order.
	 * </p>
	 *
	 * <p>
	 * This controls the entry's schema layout without changing the record's
	 * constructor order. In schema-first mode, the resulting layout must match the
	 * XML.
	 * </p>
	 */
	String[] layout() default {};

	/**
	 * Fields declared in each group entry without corresponding record components.
	 * Each field must specify its name and its {@code type()} or
	 * {@code primitiveType()}, and appear in {@link #layout()}. Only fields can be
	 * unmapped, not groups or variable-length data, and codecs do not yet support
	 * an unmapped field of a composite type.
	 *
	 * <p>
	 * This allows a retired field to keep its place on the wire after its component
	 * is removed. Encoding writes its null value: in every element of an array, no
	 * bits for a set, nothing for a constant. Decoding skips it.
	 * </p>
	 */
	SbeField[] unmapped() default {};

	/**
	 * The binding between the record component's application type and a
	 * {@code List<E>}, where {@code E} is the group entry record.
	 *
	 * <p>
	 * When unspecified, the component must itself be a list of entry records. A
	 * binding does not change the schema.
	 * </p>
	 *
	 * @see TypeBinding
	 */
	Class<?> binding() default void.class;
}
