package com.example.trading;

import static net.concini.sbebuddy.PrimitiveType.UINT16;
import static net.concini.sbebuddy.PrimitiveType.UINT32;

import net.concini.sbebuddy.MessageHeader;
import net.concini.sbebuddy.SbeComposite;
import net.concini.sbebuddy.SbeType;

/**
 * The header every message of the session is framed in: the standard four and
 * the session's sequence number, which a gateway passes on.
 */
@SbeComposite(description = "The standard four and the session's sequence number")
public record SessionHeader(
		@SbeType(primitiveType = UINT16) int blockLength,
		@SbeType(primitiveType = UINT16) int templateId,
		@SbeType(primitiveType = UINT16) int schemaId,
		@SbeType(primitiveType = UINT16) int version,
		@SbeType(primitiveType = UINT32) long sequenceNumber
) implements MessageHeader {
}
