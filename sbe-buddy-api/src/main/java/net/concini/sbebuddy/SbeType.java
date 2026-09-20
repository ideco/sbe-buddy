package net.concini.sbebuddy;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The {@code type} element, on a final class or on a composite's component;
 * {@code value} is its text, the constant.
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.TYPE, ElementType.RECORD_COMPONENT})
public @interface SbeType {

	PrimitiveType primitiveType();

	String name() default "";

	String value() default "";

	int length() default 1;

	String characterEncoding() default "";

	Presence presence() default Presence.REQUIRED;

	String valueRef() default "";

	String nullValue() default "";

	String minValue() default "";

	String maxValue() default "";

	int offset() default 0;

	String semanticType() default "";

	String description() default "";

	int sinceVersion() default 0;

	int deprecated() default 0;
}
