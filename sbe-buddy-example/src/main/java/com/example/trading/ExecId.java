package com.example.trading;

import static net.concini.sbebuddy.PrimitiveType.CHAR;

import net.concini.sbebuddy.SbeType;

/** The id the venue gives an execution. */
@SbeType(primitiveType = CHAR, length = 16, semanticType = "String")
public final class ExecId {

	private ExecId() {
	}
}
