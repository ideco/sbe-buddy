package corpus.bindings;

import net.concini.sbebuddy.SbeField;

record Leg(
		@SbeField(id = 1) int legId,
		@SbeField(id = 2) int ratio
) {
}
