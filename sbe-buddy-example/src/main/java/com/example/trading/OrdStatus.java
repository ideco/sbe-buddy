package com.example.trading;

import net.concini.sbebuddy.SbeEnum;
import net.concini.sbebuddy.SbeEnumValue;
import net.concini.sbebuddy.UnknownValue;

/** Where an order stands. */
@SbeEnum(encodingType = CharEnum.class)
public enum OrdStatus {

	@SbeEnumValue("0")
	NEW,

	@SbeEnumValue("1")
	PARTIALLY_FILLED,

	@SbeEnumValue("2")
	FILLED,

	@SbeEnumValue("4")
	CANCELED,

	@SbeEnumValue("8")
	REJECTED,

	/** A value this schema does not name, from a venue that added it. */
	@UnknownValue
	UNKNOWN
}
