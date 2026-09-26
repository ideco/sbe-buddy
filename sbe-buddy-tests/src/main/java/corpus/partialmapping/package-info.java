/**
 * A schema read from its XML and mapped in part: a record for {@code Order}
 * alone, so {@code Quote} and {@code Heartbeat} get sbe-tool's flyweights and
 * no codec, and {@code Price}, which only {@code Quote} reaches, needs no
 * declaration.
 */
@NullMarked
@SbeSchema(id = 1, version = 0, resource = "schema.xml", partial = true)
package corpus.partialmapping;

import org.jspecify.annotations.NullMarked;

import net.concini.sbebuddy.SbeSchema;
