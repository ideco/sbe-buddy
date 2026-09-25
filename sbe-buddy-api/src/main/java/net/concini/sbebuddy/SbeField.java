package net.concini.sbebuddy;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a field in the fixed-length block of a message or repeating group.
 * On a record component, it maps the component to that field. It can also
 * declare an unmapped field through {@link SbeMessage#unmapped()}.
 *
 * <p>
 * The field's wire type is inferred from the record component unless
 * {@link #type()} or {@link #primitiveType()} specifies it explicitly. A
 * {@link #binding()} converts between an application type and the Java
 * representation of the wire value.
 * </p>
 *
 * <p>
 * In schema-first mode, the field declaration must match the XML schema.
 * </p>
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.RECORD_COMPONENT)
public @interface SbeField {

	/**
	 * The field's ID in its containing message or group.
	 */
	int id();

	/**
	 * The field name in the schema. An empty value uses the record component's
	 * name. Unmapped fields must specify a name.
	 */
	String name() default "";

	/**
	 * The declaration of the field's named SBE type: a type, enum, set or
	 * composite. Mutually exclusive with {@link #primitiveType()}.
	 *
	 * <p>
	 * When neither is specified, the type is inferred from the record component.
	 * Unmapped fields must specify one of them.
	 * </p>
	 */
	Class<?> type() default void.class;

	/**
	 * The field's SBE primitive type. Mutually exclusive with {@link #type()}.
	 *
	 * <p>
	 * When neither is specified, Java numeric primitives and their boxed types use
	 * the corresponding signed SBE type. Unsigned types and SBE's one-byte
	 * {@code char} must be specified explicitly.
	 * </p>
	 */
	PrimitiveType primitiveType() default PrimitiveType.NONE;

	/**
	 * Whether the field is required, optional or constant.
	 *
	 * <ul>
	 * <li>{@link Presence#REQUIRED}: the field must have a value.</li>
	 * <li>{@link Presence#OPTIONAL}: the field may represent an absent value.</li>
	 * <li>{@link Presence#CONSTANT}: the value is defined by the schema and
	 * occupies no bytes. Decoding supplies the constant; encoding checks that the
	 * supplied value matches it.</li>
	 * </ul>
	 *
	 * <p>
	 * The default is omitted from the generated XML. A named type's presence
	 * therefore applies when specified on that type; otherwise the field is
	 * required.
	 * </p>
	 *
	 * <p>
	 * For optional scalar and enum fields, the wire null value maps to
	 * {@code null}. Optional sets, arrays, strings and composites require a binding
	 * to represent {@code null}; without one, null components are rejected.
	 * </p>
	 *
	 * <p>
	 * A constant field must specify {@link #valueRef()} or name a constant type
	 * through {@link #type()}.
	 * </p>
	 */
	Presence presence() default Presence.REQUIRED;

	/**
	 * A reference to an enum value, in the form {@code EnumName.ValueName}, used to
	 * supply a constant field's value. Names refer to the SBE schema. Empty means
	 * unspecified.
	 */
	String valueRef() default "";

	/**
	 * The field's byte offset from the start of its message's fixed-length block or
	 * its group entry, excluding the message or group header.
	 *
	 * <p>
	 * Zero leaves the offset unspecified for sbe-tool to calculate from the
	 * preceding fields. An explicit offset may leave padding but must not overlap a
	 * preceding field.
	 * </p>
	 */
	int offset() default 0;

	/**
	 * The field's epoch label. Empty means unspecified.
	 *
	 * <p>
	 * Written to the schema and supplied to bindings through
	 * {@link BindingContext}. sbe-buddy does not interpret it.
	 * </p>
	 */
	String epoch() default "";

	/**
	 * The field's time-unit label. Empty means unspecified.
	 *
	 * <p>
	 * Written to the schema and supplied to bindings through
	 * {@link BindingContext}. sbe-buddy does not interpret it.
	 * </p>
	 *
	 * @deprecated SBE deprecates this attribute on fields. Retained for schemas
	 *             written against release candidate 2; declare the unit on the type
	 *             instead, for example on a composite member.
	 */
	@Deprecated
	String timeUnit() default "";

	/**
	 * A semantic type label for the field. Empty means unspecified.
	 */
	String semanticType() default "";

	/**
	 * A description of the field. Empty means unspecified.
	 */
	String description() default "";

	/**
	 * The schema version in which the field was introduced.
	 *
	 * <p>
	 * When decoding a version older than this field, a nonconstant field decodes to
	 * {@code null}. Primitive components must use their boxed types, such as
	 * {@link Long} instead of {@code long}, if the codec accepts those older
	 * versions.
	 * </p>
	 *
	 * @see SbeSchema#baselineVersion()
	 */
	int sinceVersion() default 0;

	/**
	 * The schema version in which the field was deprecated. Zero means unspecified.
	 * Deprecation does not remove the field.
	 */
	int deprecated() default 0;

	/**
	 * The binding between the record component's application type and the Java
	 * representation of the field's wire value.
	 *
	 * <p>
	 * When unspecified, the component must use the Java representation expected for
	 * the wire type. A binding does not change the schema and cannot be specified
	 * on an unmapped field.
	 * </p>
	 *
	 * @see TypeBinding
	 */
	Class<?> binding() default void.class;
}
