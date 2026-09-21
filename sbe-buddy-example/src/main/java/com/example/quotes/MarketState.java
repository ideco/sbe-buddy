package com.example.quotes;

import static net.concini.sbebuddy.PrimitiveType.CHAR;

import net.concini.sbebuddy.SbeEnum;
import net.concini.sbebuddy.SbeEnumValue;

/**
 * The state of the instrument's market. A state this reader does not know is
 * not a quote it can use, so there is no unknown value: decoding one fails.
 */
@SbeEnum(primitiveType = CHAR, description = "The state of the instrument's market")
public enum MarketState {

	@SbeEnumValue("O")
	OPEN,

	@SbeEnumValue("H")
	HALTED,

	@SbeEnumValue("C")
	CLOSED
}
