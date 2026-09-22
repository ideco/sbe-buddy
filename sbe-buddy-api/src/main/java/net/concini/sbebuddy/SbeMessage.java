package net.concini.sbebuddy;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The {@code message} element, on a record whose components are its fields,
 * then groups, then data.
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface SbeMessage {

	int id();

	String name() default "";

	int blockLength() default 0;

	String semanticType() default "";

	String description() default "";

	int sinceVersion() default 0;

	int deprecated() default 0;

	/**
	 * The body's components and unmapped fields in wire order, by name: a component
	 * by its Java name, an unmapped field by its {@code name}. Empty, the order is
	 * declaration order. The Java side; contributes nothing to the schema.
	 */
	String[] layout() default {};

	/**
	 * Fields of the body no component carries, each complete with its {@code name}
	 * and its type: the codec writes the null value and skips them on the way back,
	 * so a record can retire a field the schema keeps. They take their place
	 * through {@link #layout()}. The Java side; contributes nothing to the schema.
	 */
	SbeField[] unmapped() default {};
}
