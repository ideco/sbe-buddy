package com.example.trading;

import net.concini.sbebuddy.SbeEnum;
import net.concini.sbebuddy.SbeEnumValue;

/** How long an order rests. */
@SbeEnum(encodingType = CharEnum.class)
public enum TimeInForce {

	@SbeEnumValue("0")
	DAY,

	@SbeEnumValue("1")
	GOOD_TILL_CANCEL,

	@SbeEnumValue("3")
	IMMEDIATE_OR_CANCEL,

	@SbeEnumValue("4")
	FILL_OR_KILL
}
