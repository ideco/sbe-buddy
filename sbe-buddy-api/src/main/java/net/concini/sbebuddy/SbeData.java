package net.concini.sbebuddy;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The {@code data} element, on a {@code String} or {@code byte[]} component.
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.RECORD_COMPONENT)
public @interface SbeData {

	int id();

	/** A composite of the {@code length}, {@code varData} shape. */
	Class<?> type();

	String name() default "";

	int offset() default 0;

	String semanticType() default "";

	String description() default "";

	int sinceVersion() default 0;

	int deprecated() default 0;
}
