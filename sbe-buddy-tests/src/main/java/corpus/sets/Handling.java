package corpus.sets;

import net.concini.sbebuddy.SbeChoice;
import net.concini.sbebuddy.SbeSet;

@SbeSet(encodingType = FlagsEncoding.class)
enum Handling {

	@SbeChoice(0)
	urgent,

	@SbeChoice(7)
	manual
}
