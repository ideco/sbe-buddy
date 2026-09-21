# SBE coverage

Current implementation coverage against **sbe-tool 1.40.2** and its `sbe.xsd`.
This is the status map; [type mappings](type-mappings.md) defines the target
contract and [project intent](intent.md) gives the implementation order.

**Schema / flyweights** means annotated Java can produce the corresponding
XML and sbe-tool's Java flyweights. **Record codec** means mapping records to
and from those bytes. Support applies to the combination described in a row,
not every possible combination of its features.

- **Supported** — implemented for the stated scope.
- **Limited** — a narrower implementation or a verification gap, described in the notes.
- **Missing** — not implemented in that layer.
- **Rejected** — deliberately disallowed by the mapping contract.
- **—** — not applicable.

For a schema containing constructs the codec cannot handle, set
`codecs = false` on `@SbeSchema` to generate XML and flyweights only. With
codecs enabled, an unsupported construct is a compilation error; generation
does not silently omit that field or produce a partial set of codecs.

## Fields and declared types

Codec entries below assume little-endian messages with the standard header
and no groups or variable-length data.

| Feature / combination | Schema / flyweights | Record codec | Notes |
| --- | --- | --- | --- |
| Signed integers, `float`, `double` | Supported | Supported | Same-width Java primitives; `Primitives` corpus. |
| Unsigned integers (`uint8` through `uint64`) | Supported | Supported | SBE's Java faces: `short`, `int`, `long`, `long`; `uint64` retains the bit pattern. |
| Scalar SBE `char` | Supported | Supported | Represented by Java `byte`; Java `char` is rejected. |
| Optional primitive fields | Supported | Supported | Boxed components; null sentinel maps to `null`, including floating-point NaN. `OptionalFields` corpus. |
| Named scalar types (`@SbeType`, length 1, nonconstant) | Supported | Limited | The emitter accepts them through the primitive path. No dedicated scalar-only codec fixture yet; `NamedTypes` also contains an unsupported string. |
| Named type attributes: bounds, null value, character encoding | Supported | Limited | Attributes reach XML/flyweights. Custom-null scalar codec behaviour lacks a dedicated fixture; strings remain unsupported. This does not promise codec validation of numeric bounds. |
| Fixed-length primitive arrays | Supported | Missing | `Arrays` corpus. |
| Fixed-length `char` strings | Supported | Missing | `NamedTypes` and `Arrays` corpus. |
| Required enum fields | Supported | Supported | Explicit wire values, never Java ordinals. `Enums` corpus and quotes example. |
| Optional enum fields | Supported | Limited | Null handling is implemented in the emitter; no dedicated optional-enum codec fixture yet. |
| Enum encoding supplied by a named type | Supported | Supported | `OrderStatus` in `Enums`. This works independently of the remaining named-field work. |
| Required set fields | Supported | Supported | `Set<E>` of the annotated enum; decoded as `EnumSet`. Empty is a value. `Sets` corpus and quotes example. |
| Set encoding supplied by a named type | Supported | Supported | `Handling` in `Sets`. |
| Optional set fields | Rejected | Rejected | A set has no null value; `MappingTest` checks the diagnostic. Version absence is a separate case below. |
| Constant types with a literal value | Supported | Missing | Includes constant strings. `Constants` corpus. |
| Constant types referring to an enum value (`valueRef`) | Supported | Missing | `BuySide` in `Constants`. |
| Constant enum fields (`presence = CONSTANT`, `valueRef`) | Supported | Missing | `side` in `Constants`. Ordinary enum support does not cover this combination. |
| Composites and nested composites | Supported | Missing | `Composites` corpus. |
| Composite members declared through `@SbeRef` | Supported | Missing | Includes explicit member offsets. |
| Inline types, enums and sets inside composites | Supported | Missing | Schema support does not imply nested record-codec support. |

### Constant enum fields

This known gap belongs to the constants work in increment 9. The target
contract is: decode to the declared enum value; encode only that value,
rejecting a different value or `null`. The field occupies no bytes. Neither
codec behaviour is implemented yet, even though schema and flyweight
generation already support it.

## Structure and layout

| Feature / combination | Schema / flyweights | Record codec | Notes |
| --- | --- | --- | --- |
| Schema and message declarations; multiple messages per package | Supported | Supported | One codec per message; `Messages` corpus. |
| Explicit field offsets and message block length | Supported | Supported | Fixed blocks; `Messages` corpus. |
| Standard `messageHeader` | Supported | Supported | Header included in encode/decode and length calculations. |
| Custom header type | Supported | Missing | `Header` corpus; the emitter currently requires the name `messageHeader`. |
| Little-endian byte order | Supported | Supported | Default. |
| Big-endian byte order | Supported | Missing | `BigEndian` corpus. |
| Repeating groups | Supported | Missing | `List<E>` declaration; `Groups` corpus. |
| Nested groups | Supported | Missing | `Groups` corpus includes nesting. |
| Custom group dimension type and group block length | Supported | Missing | `Groups` corpus. |
| Variable-length strings and binary data | Supported | Missing | `VarData` corpus; `Groups` also covers data inside groups. |
| Descriptions, semantic types, `epoch`, `timeUnit`, `semanticVersion` | Supported | — | Schema metadata; no automatic Java time conversion. |

## Evolution and unknown values

| Behaviour | Status | Scope / evidence |
| --- | --- | --- |
| `sinceVersion` and `deprecated` in the schema | Supported | `Versions` corpus; validation checks version bounds and sibling ordering. |
| Decode an older fixed block with later fields absent | Supported | Later primitive, enum and set fields become `null`; `AddedFields` and quotes cross-version tests. |
| Read acting version and block length from the header | Supported | Current codec reads older quotes; frozen older flyweights read the current block. Encoding writes the current schema version. |
| Reject messages below `baselineVersion` | Supported | Quotes cross-version tests. This is a Java-side policy, not an SBE attribute. |
| Decode an unknown enum value | Supported | Throws `IllegalArgumentException`, or returns the enum's `@UnknownValue` constant. Quotes tests cover both policies. |
| Encode the `@UnknownValue` fallback | Rejected | It has no wire representation; the original unknown value is not retained. |
| Decode unknown set bits | Rejected | Throws `IllegalArgumentException`; a `Set<E>` cannot retain unnamed bits. |
| Version metadata on enum values and set choices | Supported | Emitted in XML; unknown-value handling determines what an older codec can read. |
| Evolution through composites, groups and variable-length data | Missing | Schema metadata exists; record codecs for these constructs do not. |
| Compatibility through every construct in both directions | Missing | Existing fixed-block tests are not a complete codec evolution matrix. |

## Java-side capabilities

| Feature | Status | Notes |
| --- | --- | --- |
| `Codec<T>` and generated message codecs | Supported | Current fixed-block scope; instances are stateful, one per thread. |
| Exact encoded length and decoded-length tracking | Supported | Header included; quotes tests check lengths and nonzero offsets. Variable-length messages remain unsupported. |
| Standard header, group dimensions and var-data wire declarations | Supported | API declarations exist; codec support follows the structure table above. |
| `UuidWire` composite declaration | Supported | Describes the wire shape; does not yet map `java.util.UUID`. |
| `TypeBinding`, `@Bind`, self-binding named types | Missing | Planned contracts; not present in the API yet. |
| Sealed message families and dispatching codecs | Missing | Separate from generating several individual message codecs. |
| Built-in `UUID`, `Instant`, `LocalDate`, `LocalTime` mappings | Missing | Planned after the underlying constructs and bindings. |

## Evidence and maintenance

- The [corpus][corpus] pairs annotated declarations, schema models and XML
  oracles. Cases with codec expectations also check the emitted source.
- [SbeProcessorTest][processor-tests] compiles the corpus through the actual
  processor. This proves schema/flyweight generation for those examples;
  it is not a runtime round-trip test of every combination.
- [QuotesTest][quotes-tests] and [ReferenceFlyweightsTest][reference-tests]
  exercise record round trips, lengths, unknown values and cross-version
  interoperability with independently generated flyweights.
- [CodecEmitter][emitter] defines current codec paths and explicit refusals.
  A **Limited** entry records where the implementation is ahead of dedicated
  test evidence.
- [XsdCoverageTest][xsd-test] checks that every XSD element and attribute
  appears in an oracle, except `data/@presence`, `data/@valueRef`,
  `data/@epoch` and `data/@timeUnit`, which sbe-tool ignores and the annotations
  do not expose. Attribute occurrence does not prove every feature combination.

Update affected rows in the same change as the implementation and its tests.
Keep meaningful combinations separate: enum support does not establish
constant-enum, optional-enum or enum-in-composite support. Link detailed
mapping rules instead of repeating them here.

[corpus]: ../sbe-buddy-generator/src/test/java/net/concini/sbebuddy/generator/corpus
[processor-tests]: ../sbe-buddy-processor/src/test/java/net/concini/sbebuddy/processor/SbeProcessorTest.java
[quotes-tests]: ../sbe-buddy-example/src/test/java/com/example/quotes/QuotesTest.java
[reference-tests]: ../sbe-buddy-example/src/test/java/com/example/quotes/ReferenceFlyweightsTest.java
[emitter]: ../sbe-buddy-generator/src/main/java/net/concini/sbebuddy/generator/CodecEmitter.java
[xsd-test]: ../sbe-buddy-generator/src/test/java/net/concini/sbebuddy/generator/corpus/XsdCoverageTest.java
