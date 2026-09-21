// codecs = false until the codec covers what this schema uses: enums in
// increment 8, named types in 9, composites in 10, groups in 12, var-data in 13.
@NullMarked
@SbeSchema(id = 91, version = 0, semanticVersion = "FIX.5.0SP2", description = "A small trading schema", codecs = false)
package com.example.trading;

import org.jspecify.annotations.NullMarked;

import net.concini.sbebuddy.SbeSchema;
