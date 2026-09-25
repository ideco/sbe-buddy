package corpus.boundpaths;

import static net.concini.sbebuddy.PrimitiveType.INT64;

import java.math.BigDecimal;

import net.concini.sbebuddy.SbeComposite;
import net.concini.sbebuddy.SbeType;

@SbeComposite
record Quote(@SbeType(primitiveType = INT64, binding = Cents.class) BigDecimal bid) {
}
