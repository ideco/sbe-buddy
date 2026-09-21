package net.concini.sbebuddy;

/** Var-data holding ASCII text. */
@SbeComposite(name = "varAsciiEncoding")
public record VarAsciiEncoding(
		@SbeType(primitiveType = PrimitiveType.UINT16) int length,
		@SbeType(primitiveType = PrimitiveType.CHAR, length = 0, characterEncoding = "US-ASCII") String varData
) {
}
