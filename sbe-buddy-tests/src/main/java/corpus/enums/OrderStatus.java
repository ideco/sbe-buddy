package corpus.enums;

import net.concini.sbebuddy.SbeEnum;
import net.concini.sbebuddy.SbeEnumValue;
import net.concini.sbebuddy.UnknownValue;

@SbeEnum(encodingType = StatusCode.class)
enum OrderStatus {

	@SbeEnumValue("0")
	New,

	@SbeEnumValue("1")
	PartiallyFilled,

	@SbeEnumValue("2")
	Filled,

	@UnknownValue
	Unknown
}
