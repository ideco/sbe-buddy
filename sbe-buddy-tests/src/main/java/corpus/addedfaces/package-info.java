/**
 * Optional fields whose face has no null value of its own, a composite, a set,
 * a char array and an int array, appended in version 1: a version 0 message
 * lacks them, and each decodes to null, bound or not, without its binding.
 */
@NullMarked
@SbeSchema(id = 1, version = 1)
package corpus.addedfaces;

import org.jspecify.annotations.NullMarked;

import net.concini.sbebuddy.SbeSchema;
