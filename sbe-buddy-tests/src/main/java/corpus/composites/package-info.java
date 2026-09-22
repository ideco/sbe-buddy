/**
 * Everything a composite may hold: an inline type, an inline enum and set, a
 * nested composite with an optional and a constant member, and a ref to a type
 * declared at the top level, which is the only place a ref resolves. Offsets
 * run in declaration order. A second field binds the same composite to a
 * BigDecimal through the face record.
 */
@NullMarked
@SbeSchema(id = 1, version = 0)
package corpus.composites;

import org.jspecify.annotations.NullMarked;

import net.concini.sbebuddy.SbeSchema;
