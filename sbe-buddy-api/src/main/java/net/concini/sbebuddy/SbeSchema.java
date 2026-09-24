package net.concini.sbebuddy;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares an SBE schema for a Java package or maps the package to an existing schema.
 * By default, annotated Java declarations define the schema and its XML is generated.
 * When {@link #resource()} is set, the XML defines the schema and records map the
 * messages and members they need.
 * Both modes generate standard SBE flyweights and, unless disabled, record codecs.
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.PACKAGE)
public @interface SbeSchema {

	/**
	 * The schema ID. Must match the XML in schema-first mode.
	 */
	int id();

	/**
	 * The numeric schema version written to message headers.
	 * Must match the XML in schema-first mode.
	 */
	int version();

	/**
	 * The schema's semantic version label, independent of {@link #version()}.
	 * It does not control encoding or decoding.
	 */
	String semanticVersion() default "";

	/**
	 * A description of the schema. Empty means unspecified.
	 */
	String description() default "";

	/**
	 * The byte order used to encode the schema's values.
	 */
	ByteOrder byteOrder() default ByteOrder.LITTLE_ENDIAN;

	/**
	 * The record representing the message header shared by this schema's messages.
	 * Defaults to SBE's standard header containing block length, template ID,
	 * schema ID and version.
	 */
	Class<? extends MessageHeader> headerType() default DefaultMessageHeader.class;

	/**
	 * Whether to generate record codecs.
	 * Standard SBE flyweights are generated regardless of this setting.
	 */
	boolean codecs() default true;

	/**
	 * The classpath resource containing an existing SBE XML schema.
	 * Paths are relative to this package, or absolute when prefixed with {@code /}.
	 *
	 * <p>When set, the XML defines the schema. Records may map only some messages
	 * and members, and schema attributes stated in annotations are checked
	 * against the XML. The XML resource is not rewritten.
	 *
	 * <p>When empty, the schema is generated from the annotated Java declarations.
	 */
	String resource() default "";

	/**
	 * The oldest schema version accepted by generated codecs.
	 * Decoding a message below this version fails.
	 *
	 * <p>A required field introduced at or below this version cannot be absent
	 * due to schema evolution, so its record component can use a primitive type.
	 * This setting affects codecs only and is not written to the SBE schema.
	 */
	int baselineVersion() default 0;
}
