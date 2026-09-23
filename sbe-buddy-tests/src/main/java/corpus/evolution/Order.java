package corpus.evolution;

import java.util.List;

import org.jspecify.annotations.Nullable;

import net.concini.sbebuddy.SbeData;
import net.concini.sbebuddy.SbeField;
import net.concini.sbebuddy.SbeGroup;
import net.concini.sbebuddy.SbeMessage;
import net.concini.sbebuddy.VarStringEncoding;

@SbeMessage(id = 1)
record Order(
		@SbeField(id = 1) long orderId,
		@SbeGroup(id = 2) List<Leg> legs
) {

	record Leg(
			@SbeField(id = 3) int legId,
			@SbeField(id = 4) Price price,
			@SbeField(id = 7, sinceVersion = 1) @Nullable Integer ratio,
			@SbeGroup(id = 5) List<Allocation> allocations,
			@SbeGroup(id = 9, sinceVersion = 2) @Nullable List<Fill> fills,
			@SbeData(id = 11, type = VarStringEncoding.class, sinceVersion = 2) @Nullable String note
	) {
	}

	record Allocation(
			@SbeField(id = 6) int account,
			@SbeField(id = 8, sinceVersion = 1) @Nullable Long share
	) {
	}

	record Fill(@SbeField(id = 10) int quantity) {
	}
}
