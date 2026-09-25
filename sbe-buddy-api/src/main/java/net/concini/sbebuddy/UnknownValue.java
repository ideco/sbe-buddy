package net.concini.sbebuddy;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks the fallback constant in an {@link SbeEnum} enum for unknown wire
 * values. At most one constant may carry this annotation, in place of
 * {@link SbeEnumValue}.
 *
 * <p>
 * Decoding maps unknown values to this constant without retaining the original
 * wire value. Encoding the constant throws {@link IllegalArgumentException}
 * because it has no wire representation.
 * </p>
 *
 * <p>
 * In an optional field the wire null sentinel decodes to null; in a required
 * field, a field added in a later version, or a composite member, it is an
 * unknown value like any other and decodes to this constant. This annotation
 * adds no value to the SBE schema.
 * </p>
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.FIELD)
public @interface UnknownValue {
}
