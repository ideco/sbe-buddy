package com.example.trading;

import static net.concini.sbebuddy.PrimitiveType.CHAR;

import net.concini.sbebuddy.SbeType;

/** An instrument symbol. */
@SbeType(primitiveType = CHAR, length = 8, semanticType = "String")
public final class Symbol {

	private Symbol() {
	}
}
