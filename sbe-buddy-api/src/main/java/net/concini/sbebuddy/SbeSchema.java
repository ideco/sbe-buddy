package net.concini.sbebuddy;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The {@code messageSchema} element; its {@code package} is the Java package.
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.PACKAGE)
public @interface SbeSchema {

	int id();

	int version();

	String semanticVersion() default "";

	String description() default "";

	ByteOrder byteOrder() default ByteOrder.LITTLE_ENDIAN;

	Class<?> headerType() default MessageHeader.class;

	/**
	 * Whether a codec is generated per message. Off, the processor writes the
	 * schema and the flyweights alone. Contributes nothing to the schema.
	 */
	boolean codecs() default true;
}
