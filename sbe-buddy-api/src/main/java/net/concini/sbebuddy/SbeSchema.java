package net.concini.sbebuddy;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares an SBE schema for a Java package.
 *
 * <p>
 * By default, annotated Java declarations define the schema and its XML is
 * generated. When {@link #resource()} is set, the complete declaration must
 * match the existing XML schema, which is then used for generation; with
 * {@link #partial()}, the declaration may cover only some of its messages.
 * </p>
 *
 * <p>
 * Both modes generate standard SBE flyweights and, unless disabled, record
 * codecs.
 * </p>
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
	 * The schema's semantic version label, independent of {@link #version()}. It
	 * does not control encoding or decoding. Empty means unspecified.
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
	 * Whether to generate record codecs. Standard SBE flyweights are generated
	 * regardless of this setting.
	 */
	boolean codecs() default true;

	/**
	 * The classpath resource containing an existing SBE XML schema. Paths are
	 * relative to this package, or absolute when prefixed with {@code /}.
	 *
	 * <p>
	 * When set, the schema described by the annotations must match the resource,
	 * including every message, member, type and schema attribute, unless
	 * {@link #partial()} leaves some messages out. The comparison accounts for XSD
	 * defaults and ignores the order of top-level type declarations and messages.
	 * Every other order must match: the members of messages, groups and composites,
	 * and enum values and set choices. The resource's {@code package} must be this
	 * Java package.
	 * </p>
	 *
	 * <p>
	 * Flyweights and codecs are generated from the resource, which is not
	 * rewritten. When empty, the XML schema is generated from the annotated Java
	 * declarations.
	 * </p>
	 */
	String resource() default "";

	/**
	 * Whether the records map only some messages of the {@link #resource()}.
	 *
	 * <p>
	 * A message without a record gets flyweights and no codec, and a type needs a
	 * declaration only where a record reaches it. A message that has a record is
	 * still mapped whole, and everything the annotations declare must match the
	 * resource. With no records at all, the package generates flyweights and checks
	 * its {@link #baseline()}. Requires a resource: a schema generated from the
	 * annotations holds exactly what they declare.
	 * </p>
	 */
	boolean partial() default false;

	/**
	 * The classpath resource containing the schema version this schema must stay
	 * compatible with, an SBE XML schema as it was released. Paths are relative to
	 * this package, or absolute when prefixed with {@code /}. Empty means no
	 * baseline.
	 *
	 * <p>
	 * The schema is checked against the baseline under SBE's extension rules:
	 * messages are matched by ID; the baseline's fields, groups and var-data come
	 * first in each block, in order, with the same IDs and types; and every
	 * addition declares a {@code sinceVersion} above the baseline's version. Names,
	 * descriptions and deprecation may change, and so may presence between required
	 * and optional.
	 * </p>
	 *
	 * <p>
	 * The baseline's version is the oldest version accepted by generated codecs.
	 * Decoding a message below it fails, and a required field introduced at or
	 * below it cannot be absent due to schema evolution, so its record component
	 * can use a primitive type. Without a baseline, codecs accept every version.
	 * The baseline is not written to the SBE schema.
	 * </p>
	 */
	String baseline() default "";
}
