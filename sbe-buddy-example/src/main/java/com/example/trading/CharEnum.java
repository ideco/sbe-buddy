package com.example.trading;

import static net.concini.sbebuddy.PrimitiveType.CHAR;

import net.concini.sbebuddy.SbeType;

/** The encoding of the enums whose values are characters, as FIX's are. */
@SbeType(primitiveType = CHAR)
public final class CharEnum {

	private CharEnum() {
	}
}
