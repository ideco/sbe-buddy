package corpus.schemafirst;

import static net.concini.sbebuddy.PrimitiveType.UINT16;
import static net.concini.sbebuddy.PrimitiveType.UINT32;
import static net.concini.sbebuddy.PrimitiveType.UINT64;

import java.util.List;
import java.util.Set;

import net.concini.sbebuddy.SbeData;
import net.concini.sbebuddy.SbeField;
import net.concini.sbebuddy.SbeGroup;
import net.concini.sbebuddy.SbeMessage;
import net.concini.sbebuddy.VarStringEncoding;

@SbeMessage(id = 1)
record BigEndian(
		@SbeField(id = 1) long orderId,
		@SbeField(id = 2) short level,
		@SbeField(id = 3, primitiveType = UINT16) int port,
		@SbeField(id = 4) int count,
		@SbeField(id = 5, primitiveType = UINT32) long sequence,
		@SbeField(id = 6, primitiveType = UINT64) long timestamp,
		@SbeField(id = 7) float ratio,
		@SbeField(id = 8) double rate,
		@SbeField(id = 9, type = Bounds.class) short[] bounds,
		@SbeField(id = 10) Venue venue,
		@SbeField(id = 11) Set<Rights> rights,
		@SbeField(id = 12) Price bid,
		@SbeGroup(id = 13) List<Fill> fills,
		@SbeData(id = 15, type = VarStringEncoding.class) String note
) {

	record Fill(@SbeField(id = 14) int quantity) {
	}
}
