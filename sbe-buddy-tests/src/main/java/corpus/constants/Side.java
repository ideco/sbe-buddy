package corpus.constants;

import static net.concini.sbebuddy.PrimitiveType.CHAR;

import net.concini.sbebuddy.SbeEnum;
import net.concini.sbebuddy.SbeEnumValue;

@SbeEnum(primitiveType = CHAR)
enum Side {

	@SbeEnumValue("B")
	Buy,

	@SbeEnumValue("S")
	Sell
}
