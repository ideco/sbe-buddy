# Use BigDecimal for a price

Use a binding when your application needs a `BigDecimal` but the SBE schema
represents a price as a mantissa and exponent. The message record will hold
the decimal value; the binding will convert it to and from the composite
record used by the codec.

This guide assumes annotation processing is already configured in your
project. If you need a project to work in, start with
[Encode and decode your first message](../tutorials/getting-started.md).
The example uses a required price with four decimal places:
`12.3456` has mantissa `123456` and exponent `-4`. It rejects values that
would need rounding.

If you are mapping an existing schema, keep its encoding and adapt the
conversion to its precision and range.

## 1. Declare the price encoding

For this example, create a schema package with this `package-info.java`:

```java
@SbeSchema(id = 100, version = 0)
package com.example.prices;

import net.concini.sbebuddy.SbeSchema;
```

In that package, create `PriceEncoding.java`:

```java
package com.example.prices;

import static net.concini.sbebuddy.Presence.CONSTANT;
import static net.concini.sbebuddy.PrimitiveType.INT64;
import static net.concini.sbebuddy.PrimitiveType.INT8;

import net.concini.sbebuddy.SbeComposite;
import net.concini.sbebuddy.SbeType;

@SbeComposite
public record PriceEncoding(
        @SbeType(primitiveType = INT64) long mantissa,
        @SbeType(primitiveType = INT8, presence = CONSTANT, value = "-4")
        byte exponent) {

    public static final byte EXPONENT = -4;
}
```

This declares the following SBE type:

```xml
<composite name="PriceEncoding">
    <type name="mantissa" primitiveType="int64"/>
    <type name="exponent" primitiveType="int8" presence="constant">-4</type>
</composite>
```

The mantissa occupies eight bytes. The exponent is a schema constant, so it
occupies no bytes. The codec supplies `-4` when decoding and checks it when
encoding.

## 2. Implement the conversion

Create `PriceBinding.java` in the same package:

```java
package com.example.prices;

import java.math.BigDecimal;
import java.math.RoundingMode;

import net.concini.sbebuddy.BindingContext;
import net.concini.sbebuddy.TypeBinding;

public final class PriceBinding
        implements TypeBinding<BigDecimal, PriceEncoding> {

    public PriceBinding() {}

    @Override
    public PriceEncoding toWire(BigDecimal value, BindingContext context) {
        final long mantissa;
        try {
            mantissa = value.setScale(-PriceEncoding.EXPONENT, RoundingMode.UNNECESSARY)
                    .unscaledValue().longValueExact();
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException(
                    context.name() + " must fit an int64 price with four decimal places: " + value,
                    e);
        }
        if (mantissa == Long.MIN_VALUE) {
            throw new IllegalArgumentException(
                    context.name() + " uses the reserved int64 null value");
        }
        return new PriceEncoding(mantissa, PriceEncoding.EXPONENT);
    }

    @Override
    public BigDecimal fromWire(PriceEncoding wire, BindingContext context) {
        if (wire.mantissa() == Long.MIN_VALUE) {
            throw new IllegalArgumentException(
                    context.name() + " uses the reserved int64 null value");
        }
        return BigDecimal.valueOf(wire.mantissa(), -wire.exponent());
    }
}
```

`toWire` converts the decimal to the schema's four-place scale, then extracts
the mantissa. `RoundingMode.UNNECESSARY` rejects a value such as `12.34567`;
`longValueExact()` rejects one whose mantissa would overflow. The explicit
check excludes the default `int64` null sentinel, which is outside this
required member's valid range. The codec does not add numeric range checks
for you.

`fromWire` reconstructs the decimal using the exponent supplied by the
codec. The context provides the component's name for error messages.

## 3. Use the binding on a message component

Create `Quote.java`:

```java
package com.example.prices;

import java.math.BigDecimal;

import net.concini.sbebuddy.SbeField;
import net.concini.sbebuddy.SbeMessage;

@SbeMessage(id = 1)
public record Quote(
        @SbeField(id = 1, type = PriceEncoding.class, binding = PriceBinding.class)
        BigDecimal price) {}
```

`type` selects the schema type. `binding` selects the Java conversion. The
field in the generated XML is:

```xml
<field name="price" id="1" type="PriceEncoding"/>
```

There is no binding declaration in the XML. A reader in another application
or language continues to read the same price encoding.

## 4. Check the result

Compile the project to generate `QuoteCodec`. With an Agrona buffer and a
`QuoteCodec` instance, run this fragment in the `com.example.prices` package:

```java
var buffer = new org.agrona.concurrent.UnsafeBuffer(new byte[64]);
var codec = new QuoteCodec();
var quote = new Quote(new java.math.BigDecimal("12.3456"));

codec.encode(quote, buffer, 0);
Quote decoded = codec.decode(buffer, 0);

System.out.println(decoded.price());
System.out.println(quote.equals(decoded));
```

Run with `--add-opens java.base/jdk.internal.misc=ALL-UNNAMED` for Agrona's
buffer access. The output is:

```text
12.3456
true
```

Try `12.34567` to check that encoding rejects excess precision. Decoded
prices always have scale 4: an input of `12.3` becomes `12.3000`.
`BigDecimal.equals` distinguishes those scales, so use `compareTo` when you
want to compare their numeric values.

This example's price is required; encoding a `null` price fails before the
binding is called. Optional composite fields need a binding that defines
their null representation, described in the
[bindings reference](../reference/bindings.md#absence).

For the design choices behind this conversion, see
[Application types and wire representations](../concepts/application-types-and-wire-representations.md).
