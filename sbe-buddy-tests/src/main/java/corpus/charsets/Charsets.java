package corpus.charsets;

import net.concini.sbebuddy.SbeData;
import net.concini.sbebuddy.SbeField;
import net.concini.sbebuddy.SbeMessage;

@SbeMessage(id = 1)
record Charsets(
		@SbeField(id = 1, type = Latin1Name.class) String latin,
		@SbeField(id = 2, type = Utf8Name.class) String unicode,
		@SbeData(id = 3, type = VarWesternEncoding.class) String western,
		@SbeData(id = 4, type = VarUtf16Encoding.class) String wide
) {
}
