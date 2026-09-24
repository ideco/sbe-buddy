/**
 * Unions over one schema's messages: a union of unions, a sealed interface
 * between them that is no union and flattens, a message in several unions, and
 * one reached twice within a union. The order messages nest inside their union,
 * the rest stand on their own.
 */
@NullMarked
@SbeSchema(id = 6, version = 0)
package corpus.unions;

import org.jspecify.annotations.NullMarked;

import net.concini.sbebuddy.SbeSchema;
