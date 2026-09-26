/**
 * A schema read from its XML with no record at all: the package is this file,
 * and it gets sbe-tool's flyweights for every message and the check that
 * version 1 still reads version 0, checked in as {@code flyweightsonly-v0.xml}.
 */
@NullMarked
@SbeSchema(id = 1, version = 1, resource = "schema.xml", partial = true, baseline = "flyweightsonly-v0.xml")
package corpus.flyweightsonly;

import org.jspecify.annotations.NullMarked;

import net.concini.sbebuddy.SbeSchema;
