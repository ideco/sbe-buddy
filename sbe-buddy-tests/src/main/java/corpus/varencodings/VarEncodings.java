package corpus.varencodings;

import org.jspecify.annotations.Nullable;

import net.concini.sbebuddy.SbeData;
import net.concini.sbebuddy.SbeField;
import net.concini.sbebuddy.SbeMessage;
import net.concini.sbebuddy.VarAsciiEncoding;
import net.concini.sbebuddy.VarDataEncoding;
import net.concini.sbebuddy.VarStringEncoding;

@SbeMessage(id = 1)
record VarEncodings(
		@SbeField(id = 1) int id,
		@SbeData(id = 2, type = VarStringEncoding.class) String text,
		@SbeData(id = 3, type = VarAsciiEncoding.class) String symbol,
		@SbeData(id = 4, type = VarDataEncoding.class) byte[] blob,
		@SbeData(id = 5, type = VarStringEncoding.class, sinceVersion = 1) @Nullable String comment
) {
}
