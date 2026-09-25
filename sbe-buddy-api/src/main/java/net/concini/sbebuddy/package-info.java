/**
 * Annotations and runtime contracts for describing SBE schemas with Java
 * records. Declare a schema with {@link net.concini.sbebuddy.SbeSchema} and
 * messages with {@link net.concini.sbebuddy.SbeMessage}; generated
 * {@link net.concini.sbebuddy.Codec} implementations encode and decode those
 * records.
 *
 * <p>
 * {@link net.concini.sbebuddy.TypeBinding} converts application values to and
 * from the Java representations used by the codecs. Type usages in this package
 * are non-null unless annotated {@code @Nullable}.
 * </p>
 */
@NullMarked
package net.concini.sbebuddy;

import org.jspecify.annotations.NullMarked;
