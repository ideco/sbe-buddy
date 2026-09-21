# Type mappings

**Normative.** This document defines the target Java representation of the
SBE schema model. Implementation, examples and other documentation must
conform to it unless the reference SBE schema or implementation demonstrates
that this document is wrong. Nothing here says when a row ships;
`intent.md` orders that. The reference for what an attribute means is
sbe-tool's `sbe.xsd` and its `uk.co.real_logic.sbe.xml` parser, never
memory.

## Conventions

- One annotation per XSD element, one member per XSD attribute, with the
  XSD's name and default. The XSD's `name` attribute is a member that
  defaults to the Java simple name (class, enum constant or record
  component), so a Java name can differ from a wire name a migrated XML
  schema already fixed, and the wire name is still written by hand.
- Where an XSD attribute names either a built-in primitive or a declared
  type (`type` on field and data, `encodingType` on enum and set,
  `dimensionType` on group), the annotation has two members: `primitiveType`
  (a `PrimitiveType`) for the built-in, and the attribute's own name as a
  `Class<?>` for the declared type. Exactly one is set, except where the
  component's own Java type is the declared type (below).
- Where the XSD value is element text rather than an attribute (an enum's
  `validValue`, a set's `choice`, a constant `type`'s value), the member is
  `value`.
- Numeric attributes that the XSD types as strings (`nullValue`, `minValue`,
  `maxValue`, constant `value`) are `String` members parsed exactly as
  sbe-tool parses them.
- A schema is a package. Declared types may live in any package; the
  schema references them by class. Flyweights are generated into
  `<package>.sbe`.
- The generator's model mirrors the same XSD nodes with the same names
  (`Schema.Field` for `field`, one component per attribute, `null` for
  absent), and `SchemaXml` writes one element per node and only the
  attributes that were set. A default is never written, so the XML reads
  as a person would write it.

## Schema and messages

| XSD | Java | Members |
| --- | --- | --- |
| `messageSchema` | `@SbeSchema` on `package-info.java` | `id`, `version`, `semanticVersion`, `description`, `byteOrder` (`LITTLE_ENDIAN`), `headerType` (a `@SbeComposite` class; default the standard `messageHeader` of four `uint16`, provided by the api); and the Java side, contributing nothing to the schema: `codecs` (`true`), `baselineVersion` (`0`, the oldest version the codecs still decode, at most `version`) |
| `message` | `@SbeMessage` on a record; components are the fields, groups and data in declaration order, which must be fields, then groups, then data | `id`, `name`, `blockLength`, `semanticType`, `description`, `sinceVersion`, `deprecated` |
| `field` | `@SbeField` on a record component | `id`, `name`, `type` / `primitiveType`, `presence` (`REQUIRED`, `OPTIONAL`, `CONSTANT`), `valueRef`, `offset`, `epoch`, `timeUnit`, `semanticType`, `description`, `sinceVersion`, `deprecated` |
| `group` | `@SbeGroup` on a `List<E>` component, `E` a record whose components are the group's fields, groups and data | `id`, `name`, `dimensionType` (a `@SbeComposite` class; default the standard `groupSizeEncoding`, provided by the api), `blockLength`, `semanticType`, `description`, `sinceVersion`, `deprecated` |
| `data` | `@SbeData` on a `String` or `byte[]` component | `id`, `name`, `type` (a `@SbeComposite` class of the `{length, varData}` shape), `offset`, `semanticType`, `description`, `sinceVersion`, `deprecated` |

A field, group or data component whose `type` and `primitiveType` are both
absent uses its component type: for an `@SbeEnum`, `@SbeSet` or
`@SbeComposite` type it references that declaration; for a bare Java
primitive it is the default mapping below; anything else is an error.

## Declared types (the `types` section)

| XSD | Java | Members |
| --- | --- | --- |
| `type` | `@SbeType` on a `final` class, or on a component of an `@SbeComposite` record (an inline element) | `name`, `primitiveType`, `length` (1), `characterEncoding`, `nullValue`, `minValue`, `maxValue`, `presence`, `value` (the constant), `valueRef`, `offset`, `semanticType`, `description`, `sinceVersion`, `deprecated` |
| `composite` | `@SbeComposite` on a record | `name`, `offset`, `semanticType`, `description`, `sinceVersion`, `deprecated` |
| `ref` | `@SbeRef` on a component of an `@SbeComposite` record | `value` (the declared type's class; omitted when the component type is itself the `@SbeEnum`, `@SbeSet` or `@SbeComposite`), `name`, `offset`, `sinceVersion`, `deprecated` |
| `enum` | `@SbeEnum` on a Java enum | `name`, `encodingType` / `primitiveType`, `offset`, `semanticType`, `description`, `sinceVersion`, `deprecated` |
| `validValue` | `@SbeEnumValue` on each constant | `value`, `name`, `description`, `sinceVersion`, `deprecated` |
| `set` | `@SbeSet` on a Java enum | `name`, `encodingType` / `primitiveType`, `offset`, `semanticType`, `description`, `sinceVersion`, `deprecated` |
| `choice` | `@SbeChoice` on each constant | `value` (the bit, 0 to 63), `name`, `description`, `sinceVersion`, `deprecated` |

The Java side adds two annotations that contribute nothing to the schema:
`@UnknownValue` on one constant of an `@SbeEnum` in place of
`@SbeEnumValue` (unknown values, below), and `@Bind` on a component
(bindings, below).

Inside an `@SbeComposite` record every component is one of: `@SbeType` (an
inline `type` element), `@SbeRef` (a `ref` element), or a component whose
type is an `@SbeEnum`, `@SbeSet` or `@SbeComposite` declared as a member type
*nested inside this record*, which is the XSD's inline `enum`, `set` or
`composite` declaration. A composite element is never inferred from a bare
Java type.

## Default mapping

A component with neither `type` nor `primitiveType`:

| Java | SBE |
| --- | --- |
| `byte` | `int8` |
| `short` | `int16` |
| `int` | `int32` |
| `long` | `int64` |
| `float` | `float` |
| `double` | `double` |

Always same-width signed. Unsigned types and `char` are written explicitly.
Java `char` is rejected: it is 16 bits, SBE `char` is one byte.

## Faces

The face is the Java type sbe-tool's flyweight exposes for an encoding. A
component's type, or a binding's `W`, must be the face of the type the
field names; boxed where the field can be absent.

| SBE encoding | Face |
| --- | --- |
| `int8`, `int16`, `int32`, `int64`, `float`, `double` | `byte`, `short`, `int`, `long`, `float`, `double` |
| `uint8`, `uint16`, `uint32`, `uint64` | `short`, `int`, `long`, `long` (sbe-tool's widening; `uint64` is the bit pattern) |
| `char` (`length` 1) | `byte` |
| `char`, `length = N` | `String`: decoded up to the first NUL, encoded NUL-padded; `IllegalArgumentException` over `N` or outside `characterEncoding` |
| any other primitive, `length = N` | the primitive's array type (`byte[]`, `short[]`, `int[]`, `long[]`, `float[]`, `double[]`), exactly `N` long |
| `enum` | the `@SbeEnum` enum |
| `set` | `Set<E>` of the `@SbeSet` enum; decoded as an `EnumSet` |
| `composite` | the `@SbeComposite` record |
| `data` with `characterEncoding` | `String` |
| `data` without | `byte[]` |
| `group` | `List<E>` |

## Absence

A field, element, group or data is *absent* when it is `presence="optional"`
and holds the null value, or when its `sinceVersion` is above the acting
version of the message being decoded. Absent decodes to `null`; the component
is boxed if its face is a primitive. Encoding `null` writes the null value for
an optional field and is an `IllegalArgumentException` for a required one. A
group that is present with zero entries is an empty list, not `null`. The
null value is the type's `nullValue` or the primitive's default, exactly as
sbe-tool applies it. A field left at the default presence takes its named
type's, as sbe-tool reads the document.

A set field cannot be optional: a set has no null value, and an empty set is
a value; the compiler rejects `presence = OPTIONAL` on one. A field of an
enum or a set is absent below the acting version like any other.

A field *can be absent* when it is optional, or when its `sinceVersion` is
above the schema's `baselineVersion` and it is not a constant. A primitive
component on a field that can be absent is an error, because `null` has
nowhere to go; a box on a field that never is stays allowed and is a
warning, since the box may be there for reasons of the user's own, and the
codec never hands it `null`. Raising the baseline is how a schema retires
its oldest versions: a required field appended at or below it is a plain
primitive again, and a message with a header version below the baseline is
an `IllegalArgumentException` on decode, never read as null values into
primitives.

## Unknown values

A newer writer may send an enum value the reader's schema does not know, a
`validValue` added with a later `sinceVersion`. That value is *unknown*: not
absent, because the null value was not written, and not representable,
because the Java enum has no constant for it. The codec reads the field raw
and maps the wire value to the constant whose `@SbeEnumValue` carries it,
never through the flyweight's generated enum.

- By default, decoding an unknown value is an `IllegalArgumentException`
  naming the enum and the value: the message cannot become the record it
  maps to. This is what sbe-tool's own Java flyweights do.
- An enum may designate one constant as its unknown value with
  `@UnknownValue` in place of `@SbeEnumValue`. It is the Java side, like
  `@Bind`, and contributes no `validValue` to the schema. Every unknown wire
  value decodes to it, so a reader that opts in keeps working when a writer
  adds values. Encoding it is an `IllegalArgumentException`: it has no wire
  form, and writing the null value in its place would be a silent loss.
- A set has no unknown value: a bit no `@SbeChoice` names is an
  `IllegalArgumentException` on decode, because a `Set<E>` cannot carry it.

The unknown value and absence stay distinct: the null value decodes to
`null`, an unknown value to the designated constant or an exception.

## Constants

A `presence="constant"` field or type carries no bytes. Its record component
still exists: decoding fills it with the constant, encoding requires the
component to equal the constant and throws otherwise. The constant is
`value` on the `@SbeType`, or `valueRef` (`Enum.CONSTANT`) on the field. A
constant is never absent, whatever its `sinceVersion`: its value is in the
schema, not on the wire.

## Bindings: the Java side

```java
public interface TypeBinding<J, W> { W toWire(J value); J fromWire(W wire); }
```

`@Bind(X.class)` on a component names a stateless binding with a public
no-arg constructor; `J` is the component type, `W` the face of the field's
SBE type. A `@SbeType` class may implement `TypeBinding` itself, in which
case `@SbeField(type = X.class)` needs no `@Bind`. A binding may check a
schema attribute it depends on (`timeUnit`, `characterEncoding`) and never
supplies one. Absence passes through as `null` without calling the binding.

Built-ins in the api: the standard `MessageHeader` and `GroupSizeEncoding`
composites; `VarStringEncoding` (UTF-8), `VarAsciiEncoding`,
`VarDataEncoding` for `@SbeData`; `UuidWire` `{int64 msb, int64 lsb}` with
`Uuid` binding `UUID`; and for `Instant`, `LocalDate` and `LocalTime` the
SBE specification's standard time encodings, `UTCTimestamp`, `LocalMktDate`
and `UTCTimeOnly`, each a wire type with its `timeUnit` explicit and a
binding beside it. Nothing else: a fixed-scale `BigDecimal` such as `Cents`
in the example below, or an `OffsetDateTime` over a `TZTimestamp` composite,
is a binding a user writes.
They carry SBE's conventional wire names through `name`, `messageHeader`,
`groupSizeEncoding`, `varStringEncoding`, `varAsciiEncoding` and
`varDataEncoding`, so a schema that uses them writes neither `headerType`
nor `dimensionType`.

## Layout and evolution

- Fields lay out in component order with sbe-tool's offset rules; `offset`
  and `blockLength` override exactly as in XML.
- Groups after fields, data after groups, in each message and each group.
- A node with `sinceVersion = n` must follow every sibling with a lower
  `sinceVersion`, and `n` is at most the schema version; `deprecated` is at
  least `sinceVersion`. The compiler rejects anything else.
- Decoding takes acting block length and acting version from the header;
  encoding always writes the schema's current version. A header version
  below `baselineVersion` is refused.
- `byteOrder` applies to every encoding of the schema.

## Families

A sealed interface in the schema package with `@SbeMessage` leaves is a
family. It carries no annotation. `<Iface>Codec implements Codec<Iface>`
decodes by template id and encodes by the record's type; a template id
outside the family is an `IllegalArgumentException`. Nested hierarchies
flatten; a record may belong to several families; every leaf must be an
`@SbeMessage` of the same schema.

## Running example, complete

The whole surface in one picture; documentation, not a fixture. The
example module holds one schema per concern instead.

```java
@SbeSchema(id = 1, version = 2)
package com.example.trading;

sealed interface IngressMessage permits PlaceOrder, CancelOrder {}

@SbeMessage(id = 1)
record PlaceOrder(
    @SbeField(id = 1) long accountId,
    @SbeField(id = 2) int quantity,
    @SbeField(id = 3, primitiveType = UINT16) int venue,
    @SbeField(id = 4, primitiveType = INT64) @Bind(Cents.class) BigDecimal price,
    @SbeField(id = 5, type = UuidWire.class) @Bind(Uuid.class) UUID orderId,
    @SbeField(id = 6) Side side,
    @SbeField(id = 7, type = Symbol.class, presence = OPTIONAL) String symbol,
    @SbeField(id = 8, type = Cents.class, sinceVersion = 1) BigDecimal commission,
    @SbeGroup(id = 9) List<Leg> legs,
    @SbeData(id = 10, type = VarStringEncoding.class, sinceVersion = 2) String note
) implements IngressMessage {}

record Leg(
    @SbeField(id = 1) long instrumentId,
    @SbeField(id = 2) int ratio) {}

@SbeEnum(primitiveType = UINT8)
enum Side { @SbeEnumValue("0") BUY, @SbeEnumValue("1") SELL }

@SbeType(primitiveType = CHAR, length = 8, characterEncoding = "US-ASCII")
final class Symbol {}

@SbeType(primitiveType = INT64)
final class Cents implements TypeBinding<BigDecimal, Long> {
    public Long toWire(BigDecimal value) { return value.movePointRight(2).longValueExact(); }
    public BigDecimal fromWire(Long wire) { return BigDecimal.valueOf(wire, 2); }
}
```
