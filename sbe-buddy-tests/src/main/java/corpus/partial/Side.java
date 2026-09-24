package corpus.partial;

import static net.concini.sbebuddy.PrimitiveType.CHAR;

import net.concini.sbebuddy.SbeEnum;
import net.concini.sbebuddy.SbeEnumValue;

@SbeEnum(primitiveType = CHAR)
enum Side {
	@SbeEnumValue("1")
	BUY, @SbeEnumValue("2")
	SELL
}
