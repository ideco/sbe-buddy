package net.concini.sbebuddy;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Generates a codec for a sealed interface representing messages from one
 * schema. The codec selects a message by record type when encoding and by the
 * header's template ID when decoding. It uses the member messages' existing
 * wire format.
 *
 * <p>
 * Every permitted subtype must be an {@link SbeMessage} record in the schema or
 * another sealed interface whose subtypes follow the same rule. Each annotated
 * interface gets its own codec; intermediate sealed interfaces need not be
 * annotated.
 * </p>
 *
 * <p>
 * The interface must be in the schema package, must not be generic, and
 * requires codec generation to be enabled. This annotation does not change the
 * SBE schema.
 * </p>
 *
 * @see Codec
 * @see SbeSchema#codecs()
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface SbeUnion {
}
