package net.concini.sbebuddy;

/** Var-data holding opaque bytes. */
@SbeComposite(name = "varDataEncoding")
public record VarDataEncoding(
		@SbeType(primitiveType = PrimitiveType.UINT16) int length,
		@SbeType(primitiveType = PrimitiveType.UINT8, length = 0) byte[] varData
) {
}
