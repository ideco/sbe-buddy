package corpus.vardata;

import java.util.List;

import net.concini.sbebuddy.SbeData;
import net.concini.sbebuddy.SbeField;
import net.concini.sbebuddy.SbeGroup;
import net.concini.sbebuddy.SbeMessage;

@SbeMessage(id = 1)
record VarData(
		@SbeField(id = 1) int orderId,
		@SbeGroup(id = 5) List<Attachment> attachments,
		@SbeData(id = 2, type = VarStringEncoding.class, semanticType = "String", description = "A free-text note") String note,
		@SbeData(id = 3, type = VarBlobEncoding.class) byte[] payload,
		@SbeData(id = 4, type = VarByteEncoding.class) byte[] signature
) {

	record Attachment(
			@SbeField(id = 6) int kind,
			@SbeData(id = 7, type = VarByteEncoding.class, offset = 4) byte[] content
	) {
	}
}
