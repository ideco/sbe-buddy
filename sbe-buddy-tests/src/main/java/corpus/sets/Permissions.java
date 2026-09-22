package corpus.sets;

import static net.concini.sbebuddy.PrimitiveType.UINT16;

import net.concini.sbebuddy.SbeChoice;
import net.concini.sbebuddy.SbeSet;

@SbeSet(primitiveType = UINT16, semanticType = "MultipleCharValue", description = "What the account may do")
enum Permissions {

	@SbeChoice(value = 0, description = "May submit orders")
	canTrade,

	@SbeChoice(value = 1, description = "May submit quotes")
	canQuote,

	@SbeChoice(2)
	canCancel
}
