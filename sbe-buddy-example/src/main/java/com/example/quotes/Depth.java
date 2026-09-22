package com.example.quotes;

import static net.concini.sbebuddy.PrimitiveType.UINT32;

import net.concini.sbebuddy.SbeType;

/** The sizes at the five best levels of one side, best first. */
@SbeType(primitiveType = UINT32, length = 5, description = "The sizes at the five best levels, best first")
public final class Depth {

	private Depth() {
	}
}
