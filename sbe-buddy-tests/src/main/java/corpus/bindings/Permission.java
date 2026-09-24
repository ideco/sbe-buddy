package corpus.bindings;

import static net.concini.sbebuddy.PrimitiveType.UINT8;

import net.concini.sbebuddy.SbeChoice;
import net.concini.sbebuddy.SbeSet;

@SbeSet(primitiveType = UINT8)
enum Permission {

	@SbeChoice(0)
	READ,

	@SbeChoice(1)
	WRITE
}
