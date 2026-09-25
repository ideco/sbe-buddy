package net.concini.sbebuddy;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a reference to a named SBE type inside a composite.
 * The annotated record component carries the referenced value.
 * Its name and offset describe this use of the type, not the type declaration.
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.RECORD_COMPONENT)
public @interface SbeRef {

	/**
	 * The named SBE type referenced by this member.
	 * When unspecified, the component's own type must be an SBE declaration.
	 * Specify the referenced type explicitly when the component uses a different
	 * Java representation or an application type with a binding.
	 */
	Class<?> value() default void.class;

	/**
	 * The member name in the schema.
	 * An empty value uses the record component's name.
	 */
	String name() default "";

	/**
	 * The member's byte offset from the start of its containing composite.
	 * Zero leaves the offset unspecified for sbe-tool to calculate.
	 * An explicit offset may leave padding but must not overlap a preceding member.
	 */
	int offset() default 0;

	/**
	 * The schema version in which the reference was introduced.
	 * This does not make a composite member conditionally absent during decoding.
	 *
	 * @see SbeComposite
	 */
	int sinceVersion() default 0;

	/**
	 * The schema version in which the member was deprecated.
	 * Zero means unspecified. Deprecation does not remove the member.
	 */
	int deprecated() default 0;

	/**
	 * The binding between the component's application type and the Java
	 * representation of the referenced type's wire value.
	 *
	 * <p>When unspecified, the component must use the expected Java representation.
	 * A binding does not change the schema.</p>
	 *
	 * @see TypeBinding
	 */
	Class<?> binding() default void.class;
}
