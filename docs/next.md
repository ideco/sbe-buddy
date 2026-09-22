# Increment 12: Codec: composites

## Goal

A field of a composite reaches the record as the `@SbeComposite` record
itself, its members mapped by the rules fields already follow: a primitive
member as its face, a string or an array as theirs, an enum, a set or a
nested composite as the record's own types, a `ref` as the record it names,
a constant filled in and checked. The composite record is the named type and
its own face, so the usual case, one Java shape per composite, costs no
second class; a field that wants another shape names a binding over the
face record, `TypeBinding<J, Price>`, which the increment 11 wrap already
serves. After it, every field of the fixed block has a codec, and what the
emitter still refuses is a group, a var-data field, a header of the
schema's own and big-endian byte order.

## Settled before it started

- sbe-tool's message flyweights expose a composite field as a flyweight of
  its own: the decoder's `<field>()` wraps a `<Composite>Decoder` at the
  field's offset and returns it, `null` below the acting version; the
  encoder's `<field>()` wraps a `<Composite>Encoder` and never guards.
  Inside a composite flyweight the members are generated as a message's
  fields are, primitives, arrays, strings, enums with `<member>Raw()`, sets
  and nested composites, but with no version guard of any kind: a member's
  `sinceVersion` changes nothing in the flyweight. A `ref` is a nested
  composite property wrapping the referenced composite's flyweight.
  (`JavaGenerator.generateCompositeProperty`, `generateComposite` and
  `generatePropertyNotPresentCondition`, read 2026-09-22; to `notes.md`.)
- A composite has no null value, so a composite field cannot be optional;
  its members may be, an inline `type` with `presence = OPTIONAL` and a
  `nullValue`, and absent decodes to `null` as a field's would. Absence by
  version inside a composite is increment 16's, since the flyweight cannot
  express it; a member's `sinceVersion` is schema documentation to the
  codec until then.
- A binding over a composite face goes through the face record: decoding
  builds the record and hands it to `fromWire`, encoding takes the record
  `toWire` returns apart. One short-lived record per bound field per
  call, and the only form a binding shipped in the api can take, since the
  flyweights live in the user's package. A user who cannot afford it makes
  the composite record the domain type.
- `layout` and `unmapped` on `@SbeComposite`, deferred from increment 9,
  land here for the members a composite writes inline: `layout` names the
  members in wire order, and `unmapped` holds complete `@SbeType` members
  no component carries, written as their null value and never read. A
  `ref`, an inline enum, set or composite cannot be unmapped in this
  increment.

## What gets built

- **The api.** `String[] layout() default {}` and `SbeType[] unmapped()
  default {}` on `@SbeComposite`, the Java side, contributing nothing to the
  schema.
- **`Annotated`.** `Type` gains a nullable `javaType`, the component's Java
  type when the type is a composite's member and `null` for a declaration
  on a class, and `Composite` gains `layout` and `unmapped`. `Discovery`
  fills them; the corpus DSL gains `javaType` on its type builder and
  `layout` and `unmapped` on its composite builder.
- **The rules, in `Mapping`,** the field rules applied to a composite's
  members, each blamed on the member: the face of an inline type, a
  primitive's, a string's or an array's, and the boxing rule over its
  presence, `Integer` where the member is optional; a `ref`'s component is
  the record it refers to, or the enum or the `Set` of the enum, as a
  field's would be; an inline enum, set or composite's component is the
  nested type, which `Discovery` already assures; a constant member takes
  a `valueRef` or a constant value. On the field: `presence = OPTIONAL` on
  a field of a composite is a problem, `Price is a composite; a field of
  it cannot be optional`, as a set's is; a binding on it takes the generic
  interface over the record, `TypeBinding<J, Price>`, which the face rule
  compares by the record's qualified name. The composite's `layout` and
  `unmapped` follow the message's rules, each naming the composite.
- **The codec emitter.** A composite is a pair of private static methods
  per composite type, keyed by wire name beside the enum and set pairs,
  `write<Composite>(<Record> value, <Composite>Encoder wire)` and
  `<Record> read<Composite>(<Composite>Decoder wire)`, in order of first
  use, a nested composite's or a `ref`'s pair declared when first reached
  inside another's. The pair maps each member with the shapes a field
  has, over the composite flyweight instead of the message's: the plain,
  optional, string, array and constant shapes, the enum and set pairs,
  `write<Nested>(value.stamp(), wire.stamp())` for a nested composite or a
  `ref`, and an unmapped member as its null value; the arguments of the
  record's constructor follow its components, as a message's do. A
  composite field encodes as `write<Composite>(value.<field>(),
  encoder.<field>())` under the checked shape, `null` refused, and
  decodes as `read<Composite>(decoder.<field>())`, under the added shape
  when appended above the baseline, since the flyweight answers `null`
  below the version. A binding wraps both as it wraps an array's pair.
  `unsupported` stops naming a composite.
- **The corpus.** `Composites` turns codecs on and gains its `CODEC` view,
  which covers the `ref`, the inline enum, set and composite, and the
  offsets sbe-tool honours without the codec knowing; it gains an optional
  member with a `nullValue` and a constant member, so both shapes show
  inside a composite, and a second field, `Decimal` bound to `BigDecimal`
  by `DecimalBinding`, a `TypeBinding<BigDecimal, Decimal>`, so the
  face-record form of a binding is proved. Its oracle grows with the
  members and the field, as the rule allows.
- **The example.** `com.example.quotes` goes to `version = 6` and appends
  `lastTrade`, a `Trade` composite of `int64 price` and `uint32 size`,
  `@Nullable Trade` since it is above the baseline; `quotes-v5.xml` is
  frozen with `xmlref.v5`. The tests: the round trip with a last trade;
  the reference decoder reading the composite's members where the codec
  wrote them; a version 5 message decoding with `null` for the trade; a
  version 5 reader reading a current message whole. `com.example.trading`
  stays on `codecs = false` for its group and var-data.
- **The guide.** The reference page the index promises, `reference/
  composites.md`: `@SbeComposite` on a record, inline types, `@SbeRef`,
  nested enums, sets and composites, a composite as a field, absence, the
  binding over the face record and when to make the record the domain
  type instead, `layout` and `unmapped`; the bindings page's coverage
  section points to it.
- **The documents.** `type-mappings.md`'s composite row takes `layout` and
  `unmapped`, its absence section the rule on composite fields and
  members, its bindings section the face-record form; `architecture.md`'s
  models, rules and generation sections follow; `notes.md` takes the facts
  above; `intent.md` ticks 12.

## Criteria

- `Composites`' emitted codec equals its `codecs` view exactly, its oracle
  parses, and every other case's view is untouched.
- The quotes example round trips with a last trade against the reference
  flyweights, in the real build, and decodes a version 5 message without
  one.
- Each rule above has a test that builds the mistake and asserts the
  `Problem` and the node.
- The guide's reference page compiles as written: its snippets are the
  example's and the corpus's.
- `./mvnw verify` is green on a fresh clone, and the CI job passes on this
  pull request.

## Out of scope

Groups and var-data, increments 13 and 14, and a header type of the
schema's own, increment 15. Absence by `sinceVersion` inside a composite,
increment 16, which the flyweight cannot express today. An unmapped `ref`,
enum, set or nested composite. A binding that reads the composite flyweight
directly, without the face record, which the api's built-ins could never
ship.
