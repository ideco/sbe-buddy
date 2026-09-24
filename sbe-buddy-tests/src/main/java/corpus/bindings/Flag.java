package corpus.bindings;

import static net.concini.sbebuddy.PrimitiveType.UINT8;

import net.concini.sbebuddy.SbeEnum;
import net.concini.sbebuddy.SbeEnumValue;

/** A yes or no on the wire, which the record holds as a boolean. */
@SbeEnum(primitiveType = UINT8)
enum Flag {

	@SbeEnumValue("0")
	NO,

	@SbeEnumValue("1")
	YES
}
