package net.concini.sbebuddy;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** The {@code ref} element, on a composite's component. */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.RECORD_COMPONENT)
public @interface SbeRef {

	/** The declared type referred to; unset when the component's own type is it. */
	Class<?> value() default void.class;

	String name() default "";

	int offset() default 0;

	int sinceVersion() default 0;

	int deprecated() default 0;

	/**
	 * A {@link TypeBinding} between the component's type and the face of the type
	 * referred to. The Java side; contributes nothing to the schema.
	 */
	Class<?> binding() default void.class;
}
