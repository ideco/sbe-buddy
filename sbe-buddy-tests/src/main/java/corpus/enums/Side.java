package corpus.enums;

import static net.concini.sbebuddy.PrimitiveType.CHAR;

import net.concini.sbebuddy.SbeEnum;
import net.concini.sbebuddy.SbeEnumValue;

@SbeEnum(primitiveType = CHAR, semanticType = "Side", description = "Which side of the market an order takes")
enum Side {

	@SbeEnumValue(value = "B", description = "The order buys")
	Buy,

	@SbeEnumValue(value = "S", description = "The order sells")
	Sell
}
