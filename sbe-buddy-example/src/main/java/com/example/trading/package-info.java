// codecs = false until the codec covers what this schema uses: composites in
// increment 12, groups in 13, var-data in 14.
@NullMarked
@SbeSchema(id = 91, version = 0, semanticVersion = "FIX.5.0SP2", description = "A small trading schema", codecs = false)
package com.example.trading;

import org.jspecify.annotations.NullMarked;

import net.concini.sbebuddy.SbeSchema;
