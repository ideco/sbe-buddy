package net.concini.sbebuddy;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** The {@code enum} element, on a Java enum. */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface SbeEnum {

	String name() default "";

	/**
	 * The encoding as a declared type; exactly one of this and
	 * {@link #primitiveType()}.
	 */
	Class<?> encodingType() default void.class;

	PrimitiveType primitiveType() default PrimitiveType.NONE;

	int offset() default 0;

	String semanticType() default "";

	String description() default "";

	int sinceVersion() default 0;

	int deprecated() default 0;
}
