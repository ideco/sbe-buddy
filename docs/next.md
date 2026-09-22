# Increment 11: Codec: bindings

## Goal

A record may hold a field as a type of its own rather than as the wire's
face: `@Bind(X.class)` on the component names a `TypeBinding<J, W>`, and
the codec maps through it, `toWire` before the flyweight's setter and
`fromWire` after its getter. A named type stays what it is, a wire encoding
the schema declares, and a binding stays Java: it contributes nothing to
the schema, another reader never sees it, and the field alone says which
binding it wants over which wire, so one domain type may arrive over
different encodings and one encoding may reach different types. The
increment covers the faces the block has so far, a primitive's, a `char`
string's and an array's; composite faces come with composites.

## Settled before it started

- A binding is stateless, has a public no-arg constructor, and the codec
  holds one instance of each binding class it uses. Absence passes through
  as `null` without calling the binding; whatever the binding throws
  passes through unwrapped (`type-mappings.md`, bindings and the codec
  contract).
- The "type that is its own binding" form, an `@SbeType` implementing
  `TypeBinding` that a field takes through `type` alone, is dropped: it
  welds a Java type to one encoding, where the field should decide. A
  class may still carry both roles, and a field that wants both says both,
  `type = Cents.class` and `@Bind(Cents.class)`. `type-mappings.md`,
  its running example and `intent.md` change in this increment.
- javac resolves a class's `TypeBinding` arguments through
  `Types.asMemberOf` on the interface's methods: the parameter type of
  `toWire` is J and its return type W, with the class's own type arguments
  substituted. To be verified by the spike and recorded in `notes.md`.

## What gets built

- **The api.** `TypeBinding<J, W>` with `W toWire(J value)` and
  `J fromWire(W wire)`, documented as the contract: stateless, a public
  no-arg constructor, never handed `null`. `@Bind` on a record component,
  `Class<? extends TypeBinding<?, ?>> value()`, retained at `CLASS`,
  documented as the Java side beside `@UnknownValue`.
- **`Annotated`.** `Field` gains `binding`, a nullable `Binding(String
  qualifiedName, JavaType wire)`: the class code names, and W as the Java
  type descriptor the face rule compares. J is not carried, because only
  javac can compare it with the component; the corpus DSL gains `bound(name,
  wire)` on its field builder.
- **The rules in `Discovery`,** on the component: `@Bind` goes with
  `@SbeField`, so on a group or data component it is a problem; the class
  implements `TypeBinding`, is not abstract and has a public no-arg
  constructor; J is the component's type, where a primitive component
  matches its box, `Cents binds BigDecimal, not long`. A `@Bind` inside an
  unmapped field is impossible, since the annotation goes on a component.
- **The rules in `Mapping`.** W is the face of the field's wire type, boxed
  where the face is a primitive because a type argument is: `Long` for
  `int64`, `String` for a `char` type with a length, `byte[]` for a `uint8`
  array: `Cents binds the wire as Long, but the face of int32 is Integer`.
  A binding on a field of an enum or a set is a problem, since their faces
  are the user's types already. The boxing rule reads the component as
  before, so a primitive J on a field that can be absent is refused as any
  primitive is; the face rule on the component yields to the binding's
  rule, since the component is J.
- **The codec emitter.** One private final field per binding class a codec
  uses, `private final com.example.Price priceBinding = new
  com.example.Price();`, named after the class's simple name, after the
  flyweight fields in order of first use; two binding classes with one
  simple name in one codec are a problem naming the message, rather than a
  mangled name. On encode, the expression the flyweight is handed becomes
  `priceBinding.toWire(value.bid())` wherever a template wrote
  `value.bid()` as the written or compared value, in the plain, optional,
  string, array and constant shapes alike, while the `null` tests stay on
  the component; on decode, the read becomes `priceBinding.fromWire(...)`
  around the flyweight's getter or the array's `read<Field>`, inside the
  optional and added shapes, so the null value and the version are decided
  before the binding is called.
- **The corpus.** A new case, `Bindings`, whose oracle is what the same
  schema writes without a binding, which is the point: `Cents`, an
  `@SbeType` of `int64` that also implements `TypeBinding<BigDecimal,
  Long>`, on a field through `type` and `@Bind` together; the same class on
  a field with `primitiveType = INT64`, sharing the instance; an optional
  field through it, `@Nullable BigDecimal`; `Symbol`, a `char` type of
  length 6, bound to a wrapper record `Ticker(String value)` by
  `TickerBinding`; and `Rgb`, a `uint8` array, bound to a record `Colour`.
  Its codec view shows the binding fields, the wrap on each side, and the
  null value decided before the binding.
- **The example.** `com.example.quotes` binds `bid` and `ask` to
  `BigDecimal` through one `Price` binding, a mantissa scaled by the
  schema's constant exponent, so the record reads `new BigDecimal("1.0050")`
  where the wire holds `10050`; the oracle does not change, and the version
  stays `5`. `symbol` stays the face, a `String`. The tests: the round trip
  with prices as decimals; the reference decoder reading the mantissas the
  binding wrote; the reference encoder's mantissas decoding to the
  decimals; a price with more than four decimals refused by the binding's
  own `ArithmeticException`, passing through the codec unwrapped.
- **The guide.** A reference page, `docs/guide/reference/bindings.md`:
  the interface and its contract, `@Bind` over a primitive and over a named
  type, a wrapper record, absence, what the binding may throw; the index
  links it, and the named-types page's coverage section points to it.
- **The documents.** `type-mappings.md`'s bindings section drops the
  own-binding form and its running example gives `commission` its `@Bind`;
  `intent.md` rewords increment 11 and ticks it; `architecture.md`'s
  models, rules and generation sections follow; `notes.md` takes the javac
  fact.

## Criteria

- `Bindings`' emitted codec equals its `codecs` view exactly, its oracle
  parses, and every other case is untouched.
- The quotes example round trips with decimal prices against the reference
  flyweights, in the real build, with the oracle unchanged.
- Each rule above has a test that builds the mistake and asserts the
  `Problem` and the node; the `Discovery` rules as processor snippets
  asserting the element.
- The guide's reference page compiles as written: its snippets are the
  example's and the corpus's.
- `./mvnw verify` is green on a fresh clone, and the CI job passes on this
  pull request.

## Out of scope

Bindings over composite faces, increment 12, and over var-data, increment
14. The built-in bindings, `Uuid` and the time encodings, increment 18. A
wrapper record taken as its own binding without a class, a convention to
revisit once the FIX schema of increment 19 shows how often the case
occurs. A binding with state, or one constructed with arguments.
