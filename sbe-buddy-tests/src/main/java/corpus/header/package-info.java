/**
 * A header of the schema's own naming and shape. sbe-tool requires the four
 * standard members and ignores anything else the header carries.
 */
@NullMarked
@SbeSchema(id = 1, version = 0, headerType = ApplicationHeader.class, codecs = false)
package corpus.header;

import org.jspecify.annotations.NullMarked;

import net.concini.sbebuddy.SbeSchema;
