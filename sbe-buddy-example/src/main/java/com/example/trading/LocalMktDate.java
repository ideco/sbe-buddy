package com.example.trading;

import static net.concini.sbebuddy.PrimitiveType.UINT16;

import net.concini.sbebuddy.SbeType;

/** A date at the market, as a count of days since 1970-01-01. */
@SbeType(primitiveType = UINT16, semanticType = "LocalMktDate")
public final class LocalMktDate {

	private LocalMktDate() {
	}
}
