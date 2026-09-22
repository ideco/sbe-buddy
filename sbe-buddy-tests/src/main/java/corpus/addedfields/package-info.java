/**
 * A message grown over two schema versions under a baseline that retired the
 * first: the field appended in version 1 is a plain primitive again, the
 * required one appended in version 2 is boxed because a version 1 message lacks
 * it, and the optional one is boxed as any optional field is.
 */
@NullMarked
@SbeSchema(id = 1, version = 2, baselineVersion = 1)
package corpus.addedfields;

import org.jspecify.annotations.NullMarked;

import net.concini.sbebuddy.SbeSchema;
