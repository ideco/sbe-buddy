/**
 * A schema on the wire the other way round, through everything whose bytes
 * depend on the order: every multi-byte width, the floating points, an array,
 * an enum and a set wider than a byte, a composite, a group's dimension and
 * var-data's length.
 */
@NullMarked
@SbeSchema(id = 1, version = 0, byteOrder = BIG_ENDIAN)
package corpus.bigendian;

import static net.concini.sbebuddy.ByteOrder.BIG_ENDIAN;

import org.jspecify.annotations.NullMarked;

import net.concini.sbebuddy.SbeSchema;
