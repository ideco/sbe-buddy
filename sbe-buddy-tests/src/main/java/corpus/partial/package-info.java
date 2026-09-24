/**
 * Part of a venue's schema, read from its XML: one message mapped by two of its
 * fields, its other fields, its group and its var-data written empty and passed
 * over on the way in; a second message mapped whole; a third with no record,
 * which gets flyweights alone.
 */
@NullMarked
@SbeSchema(id = 1, version = 0, resource = "schema.xml")
package corpus.partial;

import org.jspecify.annotations.NullMarked;

import net.concini.sbebuddy.SbeSchema;
