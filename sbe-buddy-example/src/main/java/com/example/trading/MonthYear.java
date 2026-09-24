package com.example.trading;

import static net.concini.sbebuddy.Presence.OPTIONAL;
import static net.concini.sbebuddy.PrimitiveType.UINT16;
import static net.concini.sbebuddy.PrimitiveType.UINT8;

import org.jspecify.annotations.Nullable;

import net.concini.sbebuddy.SbeComposite;
import net.concini.sbebuddy.SbeType;

/** A year and month, and optionally a day or a week of the month. */
@SbeComposite(semanticType = "MonthYear")
public record MonthYear(
		@SbeType(primitiveType = UINT16, presence = OPTIONAL) @Nullable Integer year,
		@SbeType(primitiveType = UINT8) short month,
		@SbeType(primitiveType = UINT8, presence = OPTIONAL) @Nullable Short day,
		@SbeType(primitiveType = UINT8, presence = OPTIONAL) @Nullable Short week
) {
}
