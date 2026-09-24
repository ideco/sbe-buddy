/**
 * Optional fields whose face has no null value of its own, a composite, a set
 * and a char array: each once with a binding that chooses what represents null,
 * and once without, its null refused.
 */
@NullMarked
@SbeSchema(id = 1, version = 0)
package corpus.optionalfaces;

import org.jspecify.annotations.NullMarked;

import net.concini.sbebuddy.SbeSchema;
