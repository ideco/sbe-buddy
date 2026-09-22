/**
 * Every node kind carrying both version attributes, under a schema old enough
 * to declare them: sbe-tool rejects a sinceVersion above the schema's version.
 */
@NullMarked
@SbeSchema(id = 1, version = 3, semanticVersion = "FIX.5.0SP2", codecs = false)
package corpus.versions;

import org.jspecify.annotations.NullMarked;

import net.concini.sbebuddy.SbeSchema;
