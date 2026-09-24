package com.example.trading;

import net.concini.sbebuddy.SbeEnum;
import net.concini.sbebuddy.SbeEnumValue;
import net.concini.sbebuddy.UnknownValue;

/** What an execution report reports. */
@SbeEnum(encodingType = CharEnum.class)
public enum ExecType {

	@SbeEnumValue("0")
	NEW,

	@SbeEnumValue("4")
	CANCELED,

	@SbeEnumValue("5")
	REPLACED,

	@SbeEnumValue("8")
	REJECTED,

	@SbeEnumValue("F")
	TRADE,

	/** A value this schema does not name, from a venue that added it. */
	@UnknownValue
	UNKNOWN
}
