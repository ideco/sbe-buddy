package corpus.unmappedarrays;

import static net.concini.sbebuddy.PrimitiveType.INT32;
import static net.concini.sbebuddy.PrimitiveType.UINT8;

import net.concini.sbebuddy.SbeComposite;
import net.concini.sbebuddy.SbeType;

@SbeComposite(layout = {"value",
		"reserved"}, unmapped = @SbeType(name = "reserved", primitiveType = UINT8, length = 3))
record Pad(@SbeType(primitiveType = INT32) int value) {
}
