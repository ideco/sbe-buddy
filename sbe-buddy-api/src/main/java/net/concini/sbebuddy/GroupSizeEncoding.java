package net.concini.sbebuddy;

/**
 * The standard group dimensions, which every group uses unless it names its
 * own.
 */
@SbeComposite(name = "groupSizeEncoding")
public record GroupSizeEncoding(
		@SbeType(primitiveType = PrimitiveType.UINT16) int blockLength,
		@SbeType(primitiveType = PrimitiveType.UINT16) int numInGroup
) {
}
