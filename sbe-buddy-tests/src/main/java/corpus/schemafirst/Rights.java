package corpus.schemafirst;

import static net.concini.sbebuddy.PrimitiveType.UINT32;

import net.concini.sbebuddy.SbeChoice;
import net.concini.sbebuddy.SbeSet;

@SbeSet(primitiveType = UINT32)
enum Rights {

	@SbeChoice(1)
	trade,

	@SbeChoice(30)
	quote
}
