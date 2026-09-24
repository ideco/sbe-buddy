package net.concini.sbebuddy;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * On a sealed interface in a schema package: a union of the messages beneath
 * it, which gets a codec of its own, {@code <Union>Codec}. It decodes any of
 * them to the interface by the header's template id and encodes any of them by
 * the record's type. Every permitted subtype is an {@code @SbeMessage} record
 * of the schema or a sealed interface whose own subtypes are; a nested union
 * gets its codec too. The Java side; contributes nothing to the schema.
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface SbeUnion {
}
