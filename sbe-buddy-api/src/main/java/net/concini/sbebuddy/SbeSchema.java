package net.concini.sbebuddy;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares an SBE schema for a Java package.
 *
 * <p>By default, annotated Java declarations define the schema and its XML is generated.
 * When {@link #resource()} is set, the complete declaration must match the existing
 * XML schema, which is then used for generation.</p>
 *
 * <p>Both modes generate standard SBE flyweights and, unless disabled, record codecs.</p>
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.PACKAGE)
public @interface SbeSchema {

	/**
	 * The schema ID written to message headers.
	 */
	int id();

	/**
	 * The numeric schema version written to message headers.
	 */
	int version();

	/**
	 * The schema's semantic version label, independent of {@link #version()}.
	 * It does not control encoding or decoding. Empty means unspecified.
	 */
	String semanticVersion() default "";

	/**
	 * A description of the schema.
	 * Empty means unspecified.
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
	 * <p>When set, the schema described by the annotations must match the resource,
	 * including every message, member, type and schema attribute.
	 * The comparison accounts for XSD defaults and ignores the order of top-level
	 * type declarations and messages. Other member order is preserved.</p>
	 *
	 * <p>Flyweights and codecs are generated from the resource, which is not rewritten.
	 * When empty, the XML schema is generated from the annotated Java declarations.</p>
	 */
	String resource() default "";

	/**
	 * The oldest schema version accepted by generated codecs.
	 * Decoding a message below this version fails.
	 *
	 * <p>A required field introduced at or below this version cannot be absent
	 * due to schema evolution, so its record component can use a primitive type.
	 * This setting affects codecs only and is not written to the SBE schema.</p>
	 */
	int baselineVersion() default 0;
}
