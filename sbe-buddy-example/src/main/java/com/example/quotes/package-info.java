// baselineVersion = 1: version 0 readers and writers are retired, so the field
// appended in version 1 is a plain primitive and a version 0 message is refused.
@NullMarked
@SbeSchema(id = 92, version = 9, baselineVersion = 1, description = "Top of book quotes")
package com.example.quotes;

import org.jspecify.annotations.NullMarked;

import net.concini.sbebuddy.SbeSchema;
