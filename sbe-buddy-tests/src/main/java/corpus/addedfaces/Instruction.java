package corpus.addedfaces;

import static net.concini.sbebuddy.PrimitiveType.UINT8;

import net.concini.sbebuddy.SbeChoice;
import net.concini.sbebuddy.SbeSet;

@SbeSet(primitiveType = UINT8)
enum Instruction {

	@SbeChoice(0)
	POST_ONLY,

	@SbeChoice(1)
	REDUCE_ONLY
}
