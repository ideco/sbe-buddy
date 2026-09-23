package corpus.unmappedarrays;

import java.util.List;

import net.concini.sbebuddy.SbeField;
import net.concini.sbebuddy.SbeGroup;
import net.concini.sbebuddy.SbeMessage;

@SbeMessage(id = 1, layout = {"orderId", "samples", "symbol", "label", "pad", "legs"}, unmapped = {
		@SbeField(id = 2, name = "samples", type = Samples.class),
		@SbeField(id = 3, name = "symbol", type = Symbol.class),
		@SbeField(id = 4, name = "label", type = Label.class)})
record UnmappedArrays(
		@SbeField(id = 1) long orderId,
		@SbeField(id = 5) Pad pad,
		@SbeGroup(id = 6, layout = {
				"legId",
				"reserved"}, unmapped = @SbeField(id = 8, name = "reserved", type = Samples.class)) List<Leg> legs
) {

	record Leg(@SbeField(id = 7) int legId) {
	}
}
