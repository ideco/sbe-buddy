package net.concini.sbebuddy;

/**
 * A variable-length encoding for binary data, selected through {@link SbeData#type()}.
 * The payload follows an unsigned 16-bit length prefix that counts bytes.
 *
 * <p>The message component uses {@code byte[]}, or an application type with
 * a binding; this record declares the encoding composite.</p>
 *
 * @param length the payload length in bytes
 * @param varData the payload
 */
@SbeComposite(name = "varDataEncoding")
public record VarDataEncoding(
		@SbeType(primitiveType = PrimitiveType.UINT16) int length,
		@SbeType(primitiveType = PrimitiveType.UINT8, length = 0) byte[] varData
) {
}
