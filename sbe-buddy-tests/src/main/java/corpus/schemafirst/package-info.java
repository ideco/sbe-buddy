/**
 * The big-endian schema read from its XML instead of written: the records of
 * {@code corpus.bigendian} as they are, over the schema that package writes,
 * checked in as this package's {@code schema.xml}. The switch from code-first
 * to schema-first is the one member on {@code @SbeSchema}, and the codecs and
 * flyweights are the twin's, byte for byte.
 */
@NullMarked
@SbeSchema(id = 1, version = 0, byteOrder = BIG_ENDIAN, resource = "schema.xml")
package corpus.schemafirst;

import static net.concini.sbebuddy.ByteOrder.BIG_ENDIAN;

import org.jspecify.annotations.NullMarked;

import net.concini.sbebuddy.SbeSchema;
