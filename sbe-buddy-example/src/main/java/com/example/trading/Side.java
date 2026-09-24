package com.example.trading;

import net.concini.sbebuddy.SbeEnum;
import net.concini.sbebuddy.SbeEnumValue;

/** Which side of the market an order takes. */
@SbeEnum(encodingType = CharEnum.class)
public enum Side {

	@SbeEnumValue("1")
	BUY,

	@SbeEnumValue("2")
	SELL,

	@SbeEnumValue("5")
	SELL_SHORT
}
