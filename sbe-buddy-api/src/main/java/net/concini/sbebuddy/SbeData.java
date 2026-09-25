package net.concini.sbebuddy;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares variable-length data in a message or group entry.
 * The encoded value consists of a length prefix followed by the payload.
 * Data members follow fixed-length fields and repeating groups.
 *
 * <p>Without a binding, the component is a {@link String} for text or a
 * {@code byte[]} for binary data, as determined by {@link #type()}.
 * Empty values encode a zero-length payload; null payloads cannot be encoded.</p>
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.RECORD_COMPONENT)
public @interface SbeData {

	/**
	 * The data member's ID in its containing message or group.
	 */
	int id();

	/**
	 * The composite describing the length prefix and payload, with members named
	 * {@code length} and {@code varData}. The payload member has length zero.
	 *
	 * <p>A {@code char} payload maps to {@link String}; other payloads map to
	 * {@code byte[]}. The length prefix counts payload bytes.</p>
	 *
	 * @see VarStringEncoding
	 * @see VarAsciiEncoding
	 * @see VarDataEncoding
	 */
	Class<?> type();

	/**
	 * The data member name in the schema.
	 * An empty value uses the record component's name.
	 */
	String name() default "";

	/**
	 * The offset attribute written on the SBE data element.
	 * Zero leaves it unspecified. Interpretation and validation are delegated
	 * to sbe-tool.
	 */
	int offset() default 0;

	/**
	 * A semantic type label for the data member.
	 * Empty means unspecified.
	 */
	String semanticType() default "";

	/**
	 * A description of the data member.
	 * Empty means unspecified.
	 */
	String description() default "";

	/**
	 * The schema version in which the data member was introduced.
	 * When decoding an older version, the component receives null without invoking
	 * its binding. This differs from a present value with an empty payload.
	 */
	int sinceVersion() default 0;

	/**
	 * The schema version in which the data member was deprecated.
	 * Zero means unspecified. Deprecation does not remove the data member.
	 */
	int deprecated() default 0;

	/**
	 * The binding between the component's application type and the payload's
	 * Java representation: {@link String} for text or {@code byte[]} for binary data.
	 *
	 * <p>When unspecified, the component must use that representation directly.
	 * A binding does not change the schema.</p>
	 *
	 * @see TypeBinding
	 */
	Class<?> binding() default void.class;
}
