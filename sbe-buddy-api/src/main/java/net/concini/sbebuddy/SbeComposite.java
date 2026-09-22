package net.concini.sbebuddy;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The {@code composite} element, on a record whose components are its members.
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface SbeComposite {

	String name() default "";

	int offset() default 0;

	String semanticType() default "";

	String description() default "";

	int sinceVersion() default 0;

	int deprecated() default 0;

	/**
	 * The members in wire order, by name: each component by its Java name and each
	 * unmapped member by its {@code name}, exactly once; empty for declaration
	 * order. The Java side; contributes nothing to the schema.
	 */
	String[] layout() default {};

	/**
	 * Inline {@code type} members no component carries, each complete with its
	 * {@code name} and {@code primitiveType}; the codec writes their null value and
	 * never reads them. The Java side; contributes nothing to the schema.
	 */
	SbeType[] unmapped() default {};
}
