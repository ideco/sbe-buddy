/**
 * A header of the schema's own naming and shape: the four standard members
 * sbe-tool requires, and members of its own that the codec writes from a
 * header, or as their null value when none is given.
 */
@NullMarked
@SbeSchema(id = 1, version = 0, headerType = ApplicationHeader.class)
package corpus.header;

import org.jspecify.annotations.NullMarked;

import net.concini.sbebuddy.SbeSchema;
