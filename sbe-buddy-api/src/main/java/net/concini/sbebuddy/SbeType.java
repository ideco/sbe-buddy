package net.concini.sbebuddy;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares an SBE primitive type, fixed-length array or fixed-length string. On
 * a class, conventionally final with a private constructor, it declares a named
 * type for use through {@link SbeField#type()}, {@link SbeRef}, or an enum's or
 * set's {@code encodingType}. On a composite's record component, it declares an
 * inline type; on a message's or group's component it is not used, and
 * {@link SbeField} describes the field.
 *
 * <p>
 * The annotated class describes the schema type; it is not the Java value
 * stored in a message record. That value uses the type's Java representation or
 * an application type supplied through a binding.
 * </p>
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.TYPE, ElementType.RECORD_COMPONENT})
public @interface SbeType {

	/**
	 * The SBE primitive used to encode each element. {@link PrimitiveType#NONE} is
	 * not a valid type declaration.
	 */
	PrimitiveType primitiveType();

	/**
	 * The type name in the schema. An empty value uses the class's simple name or
	 * the record component's name.
	 */
	String name() default "";

	/**
	 * The constant value, written as text in the schema. Used with
	 * {@link Presence#CONSTANT}; empty means unspecified.
	 */
	String value() default "";

	/**
	 * The number of primitive elements in the type. One represents a scalar; larger
	 * values represent a fixed-length array or, for {@code char}, a string.
	 *
	 * <p>
	 * Zero is used for the {@code varData} member of a variable-length data
	 * encoding. It does not make an ordinary field variable-length.
	 * </p>
	 */
	int length() default 1;

	/**
	 * The character encoding for text, such as {@code US-ASCII} or {@code UTF-8}.
	 * Empty leaves the attribute out of the schema, and sbe-tool then uses
	 * {@code US-ASCII}.
	 *
	 * <p>
	 * Fixed-length strings use NUL padding. For an encoding that places a zero byte
	 * inside a character, such as UTF-16, the flyweights are still generated but no
	 * codec is.
	 * </p>
	 */
	String characterEncoding() default "";

	/**
	 * Whether values of this type are required, optional or constant. A field using
	 * this type takes this presence unless the field declares optional or constant
	 * itself; since required is never written to the schema, a field cannot make an
	 * optional or constant type required.
	 *
	 * <p>
	 * Constants occupy no bytes. A constant composite member must supply
	 * {@link #value()} or {@link #valueRef()}. An optional inline composite member
	 * cannot have a {@link #length()} greater than one.
	 * </p>
	 */
	Presence presence() default Presence.REQUIRED;

	/**
	 * An enum value supplying the constant, in the form {@code EnumName.ValueName}.
	 * Names refer to the SBE schema. Empty means unspecified.
	 */
	String valueRef() default "";

	/**
	 * The null sentinel, written as text in the schema. Empty uses the SBE
	 * primitive's default null value.
	 */
	String nullValue() default "";

	/**
	 * The minimum value declared for the type, written as text in the schema. Empty
	 * uses the SBE primitive's default minimum.
	 */
	String minValue() default "";

	/**
	 * The maximum value declared for the type, written as text in the schema. Empty
	 * uses the SBE primitive's default maximum.
	 */
	String maxValue() default "";

	/**
	 * The member's byte offset from the start of its containing composite. Zero
	 * leaves the offset unspecified for sbe-tool to calculate. An explicit offset
	 * may leave padding but must not overlap a preceding member.
	 *
	 * <p>
	 * On a named type the offset is still written to the schema, and sbe-tool
	 * applies it to every reference inside a composite that sets no offset of its
	 * own. A field is positioned by {@link SbeField#offset()}.
	 * </p>
	 */
	int offset() default 0;

	/**
	 * A semantic type label for the type. Empty means unspecified.
	 */
	String semanticType() default "";

	/**
	 * A description of the type. Empty means unspecified.
	 */
	String description() default "";

	/**
	 * The schema version in which the type was introduced. On an inline composite
	 * member it may not be above the composite's own, and it does not make the
	 * member conditionally absent during decoding.
	 *
	 * @see SbeComposite
	 */
	int sinceVersion() default 0;

	/**
	 * The schema version in which the type was deprecated. Zero means unspecified.
	 * Deprecation does not remove the type.
	 */
	int deprecated() default 0;

	/**
	 * The binding between a composite component's application type and the Java
	 * representation of this type's wire value.
	 *
	 * <p>
	 * When unspecified, the component must use the expected Java representation.
	 * Bindings cannot be declared on a class annotated with {@code SbeType} or on
	 * an unmapped member. A binding does not change the schema.
	 * </p>
	 *
	 * @see TypeBinding
	 */
	Class<?> binding() default void.class;
}
