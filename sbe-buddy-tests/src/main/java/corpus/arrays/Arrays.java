package corpus.arrays;

import net.concini.sbebuddy.SbeField;
import net.concini.sbebuddy.SbeMessage;

@SbeMessage(id = 1)
record Arrays(
		@SbeField(id = 1, type = Rgb.class) byte[] colour,
		@SbeField(id = 2, type = Samples.class) int[] samples,
		@SbeField(id = 3, type = Bounds.class) short[] bounds
) {
}
