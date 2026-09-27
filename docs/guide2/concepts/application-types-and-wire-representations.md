# Application types and wire representations

An SBE schema describes how a value is encoded. Your application chooses
how to work with that value in Java. Those choices often align: an SBE
`int32` can be an `int` record component without any conversion. Sometimes
the application needs a different type, such as `BigDecimal` for a price or
`Instant` for a timestamp. A binding connects that type to the schema's
representation.

## One price, two Java types

Consider a schema that represents prices with an `int64` mantissa and a
constant exponent of `-4`. A mantissa of `123456` means `12.3456`.

In sbe-buddy, that composite is declared as a record, `PriceEncoding`, with
`mantissa` and `exponent` components. A message can use `PriceEncoding`
directly. Application code can then inspect the mantissa and exponent, and
the record can have methods that are useful to the application.

Alternatively, the message can hold a `BigDecimal`. A binding converts it
to a `PriceEncoding` before encoding, and converts the decoded
`PriceEncoding` back to a `BigDecimal`:

```text
BigDecimal  <-- binding -->  PriceEncoding  <-- codec and flyweight -->  bytes
```

The schema is the same in both cases. Other readers see the same mantissa
and the same schema constant. The binding affects how this Java application
works with the value.

## Choosing whether to use a binding

Using the composite record directly is useful when its structure already
fits the application. For example, an application that routinely works
with scaled integer prices may benefit from keeping the mantissa explicit.

A binding is useful when a different type makes the surrounding code
simpler. A calculation API might already accept `BigDecimal`, or a service
may use `Instant` consistently for timestamps. Centralizing the conversion
in a binding avoids repeating it each time a message is read or written.

The conversion also has a cost. A binding over a composite receives or
returns the composite record, so converting to another application type
introduces an intermediate value. If allocation matters in that part of
the application, consider using the composite record directly or working
with the generated flyweights for direct buffer access.

Bindings are selected per component. One message can use a composite record
directly while another uses a binding for the same schema type.

## The conversion is part of your application's contract

A Java type does not fully specify an SBE encoding. `BigDecimal` does not
choose a precision, `Instant` does not choose a time unit, and `UUID` does
not choose a byte order or layout. These decisions belong to the schema
and the application. This is why sbe-buddy does not provide built-in
bindings for those types.

The binding must account for differences between what the two types can
represent. For a price with four decimal places, `12.34567` cannot be
encoded exactly. The application must decide whether to reject it or apply
a defined rounding policy. A value can also fit in `BigDecimal` while being
too large for an `int64` mantissa.

Even an exact numeric conversion may change a Java value's equality
behavior. Decoding `12.3` from a schema with exponent `-4` produces
`12.3000`. They compare numerically equal, but `BigDecimal.equals` also
compares scale. Decide which kind of equality your application needs.

## Absence needs a representation too

An optional scalar or enum has a reserved null value. The codec can handle
that absence itself, passing Java `null` to the record without invoking a
binding.

An optional composite has no single scalar null sentinel. A price schema
might use an optional mantissa to indicate that the whole price is absent.
Its binding then interprets a composite with a null mantissa as a null
application price, and performs the reverse conversion when encoding.
This convention must agree with the schema and its other readers.

Absence due to an older schema version is different: the member was never
encoded. The codec returns `null` without invoking the binding, including
for composite fields.

The [bindings reference](../reference/bindings.md#absence) lists the rules
for each case. To implement the required-price conversion described here,
follow [Use BigDecimal for a price](../how-to/use-bigdecimal-for-a-price.md).
