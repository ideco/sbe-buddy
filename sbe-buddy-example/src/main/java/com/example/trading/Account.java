package com.example.trading;

import static net.concini.sbebuddy.PrimitiveType.CHAR;

import net.concini.sbebuddy.SbeType;

/** The account an order trades for. */
@SbeType(primitiveType = CHAR, length = 12, semanticType = "String")
public final class Account {

	private Account() {
	}
}
