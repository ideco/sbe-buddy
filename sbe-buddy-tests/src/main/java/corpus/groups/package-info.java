/**
 * A group with a layout and a field the record no longer carries, holding a
 * group whose dimensions are a composite of its own and whose entry has an
 * optional field; and a group appended in version 1 whose entry binds a price.
 */
@NullMarked
@SbeSchema(id = 1, version = 1)
package corpus.groups;

import org.jspecify.annotations.NullMarked;

import net.concini.sbebuddy.SbeSchema;
