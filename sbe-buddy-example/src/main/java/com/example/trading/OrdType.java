package com.example.trading;

import net.concini.sbebuddy.SbeEnum;
import net.concini.sbebuddy.SbeEnumValue;

/** How an order is priced. */
@SbeEnum(encodingType = CharEnum.class)
public enum OrdType {

	@SbeEnumValue("1")
	MARKET,

	@SbeEnumValue("2")
	LIMIT,

	@SbeEnumValue("3")
	STOP,

	@SbeEnumValue("4")
	STOP_LIMIT
}
