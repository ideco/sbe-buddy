package com.example.trading;

import static net.concini.sbebuddy.PrimitiveType.CHAR;

import net.concini.sbebuddy.SbeType;

/** A party to an order: a firm, a trader, a client. */
@SbeType(primitiveType = CHAR, length = 12, semanticType = "String")
public final class PartyId {

	private PartyId() {
	}
}
