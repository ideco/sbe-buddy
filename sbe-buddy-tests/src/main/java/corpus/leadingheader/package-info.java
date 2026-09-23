/**
 * A header whose member of its own comes first, as a length prefix does, so
 * every standard member sits at another offset than in the standard header.
 */
@NullMarked
@SbeSchema(id = 7, version = 2, headerType = FramingHeader.class)
package corpus.leadingheader;

import org.jspecify.annotations.NullMarked;

import net.concini.sbebuddy.SbeSchema;
