/**
 * The evolution schema at version 2, the current one: the entry appends a
 * nested group and var-data after what version 1 gave it. It reads every
 * earlier version's messages, with each member appended since then absent.
 */
@NullMarked
@SbeSchema(id = 5, version = 2)
package corpus.evolution;

import org.jspecify.annotations.NullMarked;

import net.concini.sbebuddy.SbeSchema;
