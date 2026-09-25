/**
 * Bound components whose paths spell alike: a field {@code fillsPrice} beside a
 * group {@code fills} with a field {@code price}, and the same for an array,
 * and a composite {@code Quote} with a member {@code bid} beside a field
 * {@code quoteBid}. Each keeps its own context and helpers.
 */
@NullMarked
@SbeSchema(id = 1, version = 0)
package corpus.boundpaths;

import org.jspecify.annotations.NullMarked;

import net.concini.sbebuddy.SbeSchema;
