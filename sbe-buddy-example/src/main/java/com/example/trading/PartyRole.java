package com.example.trading;

import static net.concini.sbebuddy.PrimitiveType.UINT8;

import net.concini.sbebuddy.SbeEnum;
import net.concini.sbebuddy.SbeEnumValue;

/** What a party is to an order. */
@SbeEnum(primitiveType = UINT8)
public enum PartyRole {

	@SbeEnumValue("1")
	EXECUTING_FIRM,

	@SbeEnumValue("3")
	CLIENT_ID,

	@SbeEnumValue("11")
	ENTERING_TRADER
}
