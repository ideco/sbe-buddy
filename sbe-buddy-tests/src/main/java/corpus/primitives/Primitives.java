package corpus.primitives;

import static net.concini.sbebuddy.PrimitiveType.CHAR;
import static net.concini.sbebuddy.PrimitiveType.UINT16;
import static net.concini.sbebuddy.PrimitiveType.UINT32;
import static net.concini.sbebuddy.PrimitiveType.UINT64;
import static net.concini.sbebuddy.PrimitiveType.UINT8;

import net.concini.sbebuddy.SbeField;
import net.concini.sbebuddy.SbeMessage;

@SbeMessage(id = 1)
record Primitives(
		@SbeField(id = 1, primitiveType = CHAR) byte aChar,
		@SbeField(id = 2) byte anInt8,
		@SbeField(id = 3) short anInt16,
		@SbeField(id = 4) int anInt32,
		@SbeField(id = 5) long anInt64,
		@SbeField(id = 6, primitiveType = UINT8) short aUint8,
		@SbeField(id = 7, primitiveType = UINT16) int aUint16,
		@SbeField(id = 8, primitiveType = UINT32) long aUint32,
		@SbeField(id = 9, primitiveType = UINT64) long aUint64,
		@SbeField(id = 10) float aFloat,
		@SbeField(id = 11) double aDouble
) {
}
