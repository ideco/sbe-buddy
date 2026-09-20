package net.concini.sbebuddy;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** The {@code field} element; {@code name} defaults to the component's name. */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.RECORD_COMPONENT)
public @interface SbeField {

	int id();

	String name() default "";

	/**
	 * A declared type: exactly one of this and {@link #primitiveType()}, or neither
	 * when the component's own type decides.
	 */
	Class<?> type() default void.class;

	PrimitiveType primitiveType() default PrimitiveType.NONE;

	Presence presence() default Presence.REQUIRED;

	String valueRef() default "";

	int offset() default 0;

	String epoch() default "unix";

	String timeUnit() default "nanosecond";

	String semanticType() default "";

	String description() default "";

	int sinceVersion() default 0;

	int deprecated() default 0;
}
