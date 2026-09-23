/**
 * Variable-length data: text with a character encoding, opaque bytes without
 * one, a length type wide enough to need its own maxValue, and var-data inside
 * a group's entry.
 */
@NullMarked
@SbeSchema(id = 1, version = 0)
package corpus.vardata;

import org.jspecify.annotations.NullMarked;

import net.concini.sbebuddy.SbeSchema;
