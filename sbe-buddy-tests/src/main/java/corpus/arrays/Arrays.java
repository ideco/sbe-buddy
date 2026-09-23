package corpus.arrays;

import java.util.List;

import net.concini.sbebuddy.SbeField;
import net.concini.sbebuddy.SbeGroup;
import net.concini.sbebuddy.SbeMessage;

@SbeMessage(id = 1)
record Arrays(
		@SbeField(id = 1, type = Rgb.class) byte[] colour,
		@SbeField(id = 2, type = Samples.class) int[] samples,
		@SbeField(id = 3, type = Bounds.class) short[] bounds,
		@SbeField(id = 4) Palette palette,
		@SbeGroup(id = 5) List<Swatch> swatches
) {

	record Swatch(
			@SbeField(id = 6, type = Rgb.class) byte[] colour
	) {
	}
}
