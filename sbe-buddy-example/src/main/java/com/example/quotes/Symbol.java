package com.example.quotes;

import static net.concini.sbebuddy.PrimitiveType.CHAR;

import net.concini.sbebuddy.SbeType;

/** An instrument symbol, eight ASCII characters padded with NUL. */
@SbeType(primitiveType = CHAR, length = 8, characterEncoding = "US-ASCII", semanticType = "String", description = "An instrument symbol, NUL padded")
public final class Symbol {

	private Symbol() {
	}
}
