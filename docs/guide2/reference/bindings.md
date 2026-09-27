# Bindings

A binding converts a record component's application type to and from the
Java representation expected by its codec. It is selected on the component
and does not change the SBE schema.

For a complete example, see [Use BigDecimal for a price](../how-to/use-bigdecimal-for-a-price.md).

## Declaration

A bound field specifies both its schema type and its conversion:

```java
@SbeField(id = 1, type = PriceEncoding.class, binding = PriceBinding.class)
BigDecimal price
```

Here, `PriceEncoding` is an `@SbeComposite` record and `PriceBinding`
implements `TypeBinding<BigDecimal, PriceEncoding>`. The field's XML is the
same as for an unbound component of type `PriceEncoding`:

```xml
<field name="price" id="1" type="PriceEncoding"/>
```

The application type does not select an encoding: `BigDecimal` alone says
nothing about the schema's precision or layout. Use `type` for a named
schema type or `primitiveType` for a primitive encoding.

## Binding interfaces

Choose the interface from the Java representation of the schema type.
The application's type is the `J` parameter in each case.

| SBE encoding | Java representation | Binding interface |
| --- | --- | --- |
| `int8`, scalar `char` | `byte` | `TypeBinding.OfByte<J>` |
| `int16`, `uint8` | `short` | `TypeBinding.OfShort<J>` |
| `int32`, `uint16` | `int` | `TypeBinding.OfInt<J>` |
| `int64`, `uint32`, `uint64` | `long` | `TypeBinding.OfLong<J>` |
| `float` | `float` | `TypeBinding.OfFloat<J>` |
| `double` | `double` | `TypeBinding.OfDouble<J>` |
| Fixed-length string or text variable-length data | `String` | `TypeBinding<J, String>` |
| Fixed-length array or binary variable-length data | The corresponding Java array | `TypeBinding<J, W>`, with that array type as `W` |
| Enum | The annotated Java enum `E` | `TypeBinding<J, E>` |
| Set | `Set<E>`, with `E` the annotated choices enum | `TypeBinding<J, Set<E>>` |
| Composite | The annotated record `R` | `TypeBinding<J, R>` |
| Repeating group | `List<E>`, with `E` the entry record | `TypeBinding<J, List<E>>` |

Primitive representations require the specialized interfaces. For example,
an `int64` binding implements `OfLong<J>`, not `TypeBinding<J, Long>`.
For `uint64`, the `long` carries the unchanged unsigned bit pattern.
Both `int8` and `uint8` arrays use `byte[]`; binary variable-length data also
uses `byte[]`.

## Where a binding can be used

| Component maps to | Annotation carrying `binding` |
| --- | --- |
| A message or group field | `@SbeField` |
| A repeating group | `@SbeGroup` |
| Variable-length data | `@SbeData` |
| An inline primitive, string or array member of a composite | `@SbeType` on the component |
| A reference to a named type within a composite | `@SbeRef` |

A binding belongs to a use of a type, so two fields can use the same schema
type with different bindings, or with no binding. A named type declaration
cannot select a binding for all its uses. Unmapped members have no record
component and cannot have bindings.

The binding must be a separate, concrete class with a no-argument
constructor accessible from the schema package. It must be stateless;
codecs reuse binding instances. A schema declaration such as an
`@SbeComposite` record cannot also implement the binding interface.

## Conversion and context

On encoding, the codec calls `toWire` to obtain the value it will write
through the flyweight. On decoding, it reads the value and calls `fromWire`
to obtain the record component.

Both methods receive a `BindingContext` containing the component's Java
name and applicable schema metadata, such as its primitive type, character
encoding and presence. The context describes the declared schema; it does
not describe the version of the message currently being decoded. Metadata
that does not apply is `null`.

`encodedLength` can also call `toWire` when a converted value determines the
message's size, for example for a group or string. A binding must not depend
on being called exactly once per encoded message.

The `TypeBinding` and `BindingContext` API Javadoc documents the individual
methods and context members.

## Absence

The following rules apply to nonconstant values:

| Situation | Behavior |
| --- | --- |
| An encoded message predates the member | Decoding returns `null` without calling the binding. |
| An optional scalar or enum field contains its null sentinel | Decoding returns `null` without calling the binding. |
| An optional scalar or enum component is `null` | Encoding writes the null sentinel without calling the binding. |
| A required component is `null` | Encoding rejects it before calling the binding. |
| An optional composite, set, string or array field | The binding defines how Java `null` is represented. |

For the last case, `toWire` receives a `null` application value and must
return a non-null Java representation that the codec can encode. `fromWire`
recognizes that representation and returns `null`. For example, a price
composite can represent absence through an optional mantissa; the binding
converts between a null price and a composite record with a null mantissa.

Groups and variable-length data cannot have optional presence. Their
bindings never receive `null`; a member missing from an older message is
handled by the codec. Empty values are passed to bindings normally.

## Validation

The codec's checks on the converted value still apply. A binding must
return a string or array that fits its encoding, and a bound constant must
convert to the schema's constant value. Numeric bounds are not checked by
the codec; enforce any required range or precision checks in the binding
or application.

Enum and set values are checked before `fromWire` runs. An unknown enum
value becomes the enum's `@UnknownValue` constant, if declared, or causes an
exception. Unknown set bits cause an exception. A binding cannot recover
the original unknown value from an enum fallback or intercept unknown bits.

Exceptions thrown by a binding propagate to the caller unchanged.

See [Application types and wire representations](../concepts/application-types-and-wire-representations.md)
for the tradeoffs when choosing a conversion.
