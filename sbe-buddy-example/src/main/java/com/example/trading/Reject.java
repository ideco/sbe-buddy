package com.example.trading;

import static net.concini.sbebuddy.PrimitiveType.UINT32;

import net.concini.sbebuddy.SbeData;
import net.concini.sbebuddy.SbeField;
import net.concini.sbebuddy.SbeMessage;
import net.concini.sbebuddy.VarStringEncoding;

/** A message the venue could not take, named by its sequence number. */
@SbeMessage(id = 6, semanticType = "j", description = "A message the venue could not take")
public record Reject(
		@SbeField(id = 45, primitiveType = UINT32) long refSeqNum,
		@SbeField(id = 380) BusinessRejectReason businessRejectReason,
		@SbeData(id = 58, type = VarStringEncoding.class) String text
) implements OrderEvent {
}
