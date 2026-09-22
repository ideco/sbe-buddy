/**
 * Bindings between a record's own types and the wire's faces: a specialization
 * over a primitive face, shared by a named type and a bare primitive and over
 * an optional field, and the generic interface over a string, to a wrapper
 * record, and over a byte array, to a record. The oracle is what the same
 * schema writes without any of them.
 */
@NullMarked
@SbeSchema(id = 1, version = 0)
package corpus.bindings;

import org.jspecify.annotations.NullMarked;

import net.concini.sbebuddy.SbeSchema;
