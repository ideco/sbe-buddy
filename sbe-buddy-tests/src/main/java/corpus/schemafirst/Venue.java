package corpus.schemafirst;

import static net.concini.sbebuddy.PrimitiveType.UINT16;

import net.concini.sbebuddy.SbeEnum;
import net.concini.sbebuddy.SbeEnumValue;

@SbeEnum(primitiveType = UINT16)
enum Venue {

	@SbeEnumValue("1000")
	XLON,

	@SbeEnumValue("2000")
	XNYS
}
