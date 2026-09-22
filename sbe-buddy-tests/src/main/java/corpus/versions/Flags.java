package corpus.versions;

import static net.concini.sbebuddy.PrimitiveType.UINT8;

import net.concini.sbebuddy.SbeChoice;
import net.concini.sbebuddy.SbeSet;

@SbeSet(primitiveType = UINT8, sinceVersion = 1, deprecated = 3)
enum Flags {

	@SbeChoice(value = 0, sinceVersion = 1, deprecated = 3)
	urgent
}
