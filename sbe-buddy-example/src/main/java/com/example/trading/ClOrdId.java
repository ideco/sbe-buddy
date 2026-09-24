package com.example.trading;

import static net.concini.sbebuddy.PrimitiveType.CHAR;

import net.concini.sbebuddy.SbeType;

/** The id a client gives its order, twenty ASCII characters. */
@SbeType(primitiveType = CHAR, length = 20, semanticType = "String")
public final class ClOrdId {

	private ClOrdId() {
	}
}
