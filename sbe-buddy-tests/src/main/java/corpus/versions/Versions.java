package corpus.versions;

import java.util.List;

import org.jspecify.annotations.Nullable;

import net.concini.sbebuddy.SbeData;
import net.concini.sbebuddy.SbeField;
import net.concini.sbebuddy.SbeGroup;
import net.concini.sbebuddy.SbeMessage;

@SbeMessage(id = 1, sinceVersion = 1, deprecated = 3)
record Versions(
		@SbeField(id = 1, type = Added.class, sinceVersion = 1, deprecated = 3) @Nullable Integer added,
		@SbeGroup(id = 2, sinceVersion = 2, deprecated = 3) @Nullable List<Extra> extra,
		@SbeData(id = 4, type = VarStringEncoding.class, sinceVersion = 2, deprecated = 3) @Nullable String note
) {

	record Extra(
			@SbeField(id = 3, sinceVersion = 2, deprecated = 3) Pair pair
	) {
	}
}
