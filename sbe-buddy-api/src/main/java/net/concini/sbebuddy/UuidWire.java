package net.concini.sbebuddy;

/** A UUID on the wire, most significant bits first. */
@SbeComposite
public record UuidWire(
		@SbeType(primitiveType = PrimitiveType.INT64) long msb,
		@SbeType(primitiveType = PrimitiveType.INT64) long lsb
) {
}
