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

	/**
	 * The header every message of the schema is framed in: the standard
	 * {@link DefaultMessageHeader}, or a record of the schema's own.
	 */
	Class<? extends MessageHeader> headerType() default DefaultMessageHeader.class;

	/**
	 * Whether a codec is generated per message. Off, the processor writes the
	 * schema and the flyweights alone. Contributes nothing to the schema.
	 */
	boolean codecs() default true;

	/**
	 * The schema's XML on the class path, when the schema exists already and the
	 * records map it: relative to the package, or absolute with a leading slash.
	 * sbe-tool generates every message's flyweights from it, the records map the
	 * messages they choose to, and every member the annotations state is checked
	 * against it. Nothing is written: the resource is the schema. Empty, the schema
	 * is written from the annotations. Contributes nothing to the schema.
	 */
	String resource() default "";

	/**
	 * The oldest version the codecs still decode: a required field added at or
	 * below it is never absent, so its component is a plain primitive rather than a
	 * box, and a message of an older version is refused. Contributes nothing to the
	 * schema.
	 */
	int baselineVersion() default 0;
}
