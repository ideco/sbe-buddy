/**
 * Fixed-length fields no component carries: an array, an ASCII string and a
 * UTF-8 string on the message, an array as a composite's member and one in a
 * group's entry. Each is written as its null value, element by element.
 */
@NullMarked
@SbeSchema(id = 1, version = 0)
package corpus.unmappedarrays;

import org.jspecify.annotations.NullMarked;

import net.concini.sbebuddy.SbeSchema;
