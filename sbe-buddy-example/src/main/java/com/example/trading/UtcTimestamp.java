package com.example.trading;

import static net.concini.sbebuddy.PrimitiveType.UINT64;

import net.concini.sbebuddy.SbeType;

/**
 * A point in time as a count since the Unix epoch. The schema gives no
 * {@code timeUnit}; {@link UtcTimestampBinding} reads that as nanoseconds.
 */
@SbeType(primitiveType = UINT64, semanticType = "UTCTimestamp")
public final class UtcTimestamp {

	private UtcTimestamp() {
	}
}
