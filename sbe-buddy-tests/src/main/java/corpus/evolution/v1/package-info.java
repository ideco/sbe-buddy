/**
 * The evolution schema at version 1: the entry appends a field after its
 * composite, and the nested group's entries append one too. Fields appended to
 * a block are the growth readers of either version step over.
 */
@NullMarked
@SbeSchema(id = 5, version = 1)
package corpus.evolution.v1;

import org.jspecify.annotations.NullMarked;

import net.concini.sbebuddy.SbeSchema;
