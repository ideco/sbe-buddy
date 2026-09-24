/**
 * Order entry in FIX's shapes without being FIX: tag numbers as ids, FIX's
 * message types as semantic types and its values in the enums, decimals with
 * constant exponents, times and dates as counts. The showcase: every construct
 * where such a schema would use it, beside the bindings its user writes. The
 * schema is frozen as {@code trading.xml} beside the package and read from
 * there; the compiler checks these records against it.
 */
@NullMarked
@SbeSchema(id = 91, version = 0, semanticVersion = "1.0", description = "Order entry in FIX's shapes", headerType = SessionHeader.class, resource = "trading.xml")
package com.example.trading;

import org.jspecify.annotations.NullMarked;

import net.concini.sbebuddy.SbeSchema;
