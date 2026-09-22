package corpus.versions;

import static net.concini.sbebuddy.PrimitiveType.UINT8;

import net.concini.sbebuddy.SbeEnum;
import net.concini.sbebuddy.SbeEnumValue;

@SbeEnum(primitiveType = UINT8, sinceVersion = 1, deprecated = 3)
enum Status {

	@SbeEnumValue(value = "1", sinceVersion = 1, deprecated = 3)
	New
}
