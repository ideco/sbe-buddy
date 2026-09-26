// The baseline is version 1, checked in as released: version 0 readers and
// writers are retired, so the field appended in version 1 is a plain primitive
// and a version 0 message is refused, and every later version is held against it.
@NullMarked
@SbeSchema(id = 92, version = 9, baseline = "quotes-v1.xml", description = "Top of book quotes")
package com.example.quotes;

import org.jspecify.annotations.NullMarked;

import net.concini.sbebuddy.SbeSchema;
