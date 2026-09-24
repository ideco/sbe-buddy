package com.example.trading;

import net.concini.sbebuddy.SbeEnum;
import net.concini.sbebuddy.SbeEnumValue;

/** What names the instrument. */
@SbeEnum(encodingType = CharEnum.class)
public enum SecurityIdSource {

	@SbeEnumValue("4")
	ISIN,

	@SbeEnumValue("8")
	EXCHANGE_SYMBOL
}
