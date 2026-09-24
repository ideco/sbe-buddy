package net.concini.sbebuddy;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares an SBE message represented by a Java record.
 *
 * <p>The generated codec encodes the following parts in order:</p>
 * <ol>
 *   <li>The message header.</li>
 *   <li>The fixed-length block of fields.</li>
 *   <li>Repeating groups.</li>
 *   <li>Variable-length data.</li>
 * </ol>
 *
 * <p>Record components define the body order unless {@link #layout()}
 * specifies it explicitly. In schema-first mode, the complete declaration
 * must match the XML schema.</p>
 *
 * @see SbeSchema#headerType()
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface SbeMessage {

	/**
	 * The message's template ID, unique within its schema.
	 */
	int id();

	/**
	 * The message name in the schema.
	 * An empty value uses the record's simple name.
	 */
	String name() default "";

	/**
	 * The length in bytes of the message's fixed-length block, excluding the
	 * message header, groups and variable-length data.
	 *
	 * <p>Zero leaves the length unspecified for sbe-tool to calculate from the
	 * field layout. A length larger than the fields require reserves trailing
	 * space for alignment or future fields.</p>
	 *
	 * <p>In schema-first mode, an explicit block length in the XML must also
	 * be declared here.</p>
	 */
	int blockLength() default 0;

	/**
	 * A semantic type label for the message.
	 * Empty means unspecified.
	 */
	String semanticType() default "";

	/**
	 * A description of the message.
	 * Empty means unspecified.
	 */
	String description() default "";

	/**
	 * The schema version in which the message was introduced.
	 */
	int sinceVersion() default 0;

	/**
	 * The schema version in which the message was deprecated.
	 * Zero means unspecified. Deprecation does not remove the message.
	 */
	int deprecated() default 0;

	/**
	 * The wire order of record components and {@linkplain #unmapped() unmapped fields}.
	 * Names refer to Java record components or to the declared names of unmapped fields.
	 *
	 * <p>When specified, every component and unmapped field must appear exactly once.
	 * When empty, components follow their declaration order.</p>
	 *
	 * <p>This controls the schema layout without changing the record's constructor order.
	 * In schema-first mode, the resulting layout must match the XML.</p>
	 */
	String[] layout() default {};

	/**
	 * Fields declared in the schema without corresponding record components.
	 * Each field must specify its name and type, and appear in {@link #layout()}.
	 *
	 * <p>This allows a retired field to keep its place on the wire after its
	 * component is removed. Encoding writes its null or empty representation;
	 * decoding skips it.</p>
	 */
	SbeField[] unmapped() default {};
}
