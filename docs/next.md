# Increment 14: The tests module

## Goal

The corpus stops being text the generator compares and becomes code the
build compiles: one package per schema in `sbe-buddy-tests`, the records
as a user would write them, the processor on the annotation processor
path, and one test class per package that hands the harness the schema's
oracle and the values its codecs must carry to the wire and back. What the
generator's twins proved by record equality the real build proves by
compiling; what the codec views proved as text the round trips prove as
behaviour, against the generated flyweights and codecs themselves. The
compile-time rules stay unit tests, since a refusal is a compile error and
nothing else can assert its message and its node.

This increment lands in two parts. The pull request that opens it builds
the module, the harness and the first case, `corpus.composites`, as the
seed every other case copies. The remaining cases move one commit each,
by the instructions below, which are written so that they can be followed
without reading anything else.

## Settled before it started

- The processor's tests compile every corpus case with `-proc:only`,
  which runs the processor and never compiles what it writes. A generated
  codec that does not compile passes there. The seed found one: a
  composite's inline set was declared as the set's enum, `Flags flags`,
  while the codec it generates takes and returns a `Set<Flags>`. The
  face of a set is `Set<E>` everywhere, so `Discovery` now takes a
  `Set` of the nested `@SbeSet` enum as the inline set and refuses the
  bare enum with `Flags is a set; use Set<Flags>`; the guide's composites
  page follows.
- The tests module is the one place the repository scans a classpath:
  `Cases` walks the module's own test classes and instantiates every
  class implementing `SchemaCase`, ordered by name, so a new schema needs
  no registration. `java-style.md` records the exception.
- A case is named `<Name>Test`, so surefire runs its own `@Test` methods
  as well as the harness finding it. Surefire's phrased reporter names
  each dynamic check by the case's description, the word `checks`, and
  the check's own description; the case's own tests are named by their
  methods, sentences in camelCase as everywhere else.
- Decoded values are compared by AssertJ's recursive comparison, so a
  record holding an array or a `Set` compares by content; the bytes a
  decoded value encodes to are compared exactly, which is the stronger
  check.
- `XsdCoverageTest` moves to the tests module now and reads every oracle
  in both places, the generator's remaining cases through `Corpus.CASES`
  from the generator's test jar and the moved cases through `Cases`, so a
  port never has to reason about coverage.
- The example module stays what it is, the realistic schema and the
  interop with sbe-tool's reference flyweights; the corpus's edge shapes
  would muddy it.

## What gets built

- **The module.** `sbe-buddy-tests`, after the example in the root
  `pom.xml`, built like the example: `sbe-buddy-api` in compile scope,
  the processor on `annotationProcessorPaths`, the generator and its test
  jar and `xmlunit-assertj3` in test scope for `SchemaXmlAssert`. Not
  deployed. The surefire configuration for phrased names in its `pom.xml`.
- **The harness**, in `net.concini.sbebuddy.tests` under the test
  sources. `SchemaCase`, the interface a case implements: `description()`,
  one line; `oracle()`, the hand-written XML as a text block;
  `roundTrips()`, a list of `RoundTrip<T>(description, codec, value)`.
  `Cases`, the discovery. `SchemaCasesTest`, the `@TestFactory`: per case
  a `DynamicContainer` named by the description, holding the oracle check
  and one `DynamicTest` per round trip. The round trip: `encodedLength`,
  a buffer of that length plus sixteen bytes on either side filled with a
  marker, `encode` at offset sixteen returning the length and leaving the
  marker bytes untouched, `decodedLength` equal to the length, `decode`,
  `lastDecodedLength` equal, the decoded value equal to the original by
  recursive comparison, and the decoded value encoded again into the
  same bytes.
- **The seed.** `corpus.composites` as it was in the generator's corpus,
  one file per type, `@NullMarked` on the package and `@Nullable` on the
  optional member; `CompositesTest` with three round trips, every member
  set, every absence and bound, and the unsigned top bit, and seven tests
  of its own: `null` in a message component and in a member, the constant
  member, the unknown enum value and the unnamed set bit on decode,
  another template, and the binding's own exception passing through. The
  generator's `Composites` case is deleted and its entry in `Corpus.CASES`
  with it.
- **The ports**, one commit per case, in the order of the table below.
- **The documents.** `architecture.md`'s modules and testing sections;
  `AGENTS.md`'s module list; `java-style.md`'s scanning rule;
  `intent.md` ticks 14 once every case has moved.

## How to port a case

Every case follows the same eight steps. The seed, `corpus.composites`,
is the worked example of each; when in doubt, do what it does.

1. **Read the case** in
   `sbe-buddy-generator/src/test/java/net/concini/sbebuddy/generator/corpus/<Name>.java`:
   its class javadoc, which says what the schema shows; `PACKAGE_INFO`;
   `SOURCE`; `XML`; and `CODEC`, if it has one, whose `throw` statements
   list what the codec refuses and with which message.
2. **Create the schema package** under `sbe-buddy-tests/src/main/java/`,
   the package `SOURCE` names, `corpus.<name>`. `package-info.java` is
   `PACKAGE_INFO` with the case's javadoc above it, `@NullMarked` before
   `@SbeSchema` and `import org.jspecify.annotations.NullMarked;`. Each
   top-level type of `SOURCE` goes into its own file named after it,
   package-private as it is, with the imports it needs. Keep every
   annotation and every name exactly as `SOURCE` has them; the oracle
   depends on them. Add `@Nullable` from `org.jspecify.annotations` to
   every component that may hold `null`: an optional field or member, a
   boxed primitive, a field, group or data appended above the baseline.
   Keep `codecs = false` where `PACKAGE_INFO` has it.
3. **Create the case** in `sbe-buddy-tests/src/test/java/corpus/<name>/<Name>Test.java`,
   `final class <Name>Test implements SchemaCase`, with the case's
   javadoc. `ORACLE` is `XML` verbatim as a text block. `description()`
   is one line, `<Name>: ` and what the schema shows. `oracle()` returns
   `ORACLE`. `roundTrips()` returns a `List.of` of `new RoundTrip<>(
   description, new <Message>Codec(), value)`; for a case with
   `codecs = false` it returns `List.of()`. Values are built through the
   records' canonical constructors, edge cases first: the null value on
   every optional field, meaning `null` in the record, then every
   optional field present; the bounds of every primitive, `Long.MIN_VALUE`
   and `Long.MAX_VALUE` for an `int64`, `-1L` for a `uint64` since the top
   bit set reads as negative through the `long` face; a full-length string
   and an empty one; an empty group, a group of several entries, and an
   empty nested group inside a filled one; an empty set and a full one;
   every enum value. Name each value by what it shows, as a phrase.
4. **Add the case's own tests**, `@Test` methods on the same class, one
   per refusal the codec view's `throw` statements list, asserting the
   exception type and the exact message with `assertThatThrownBy(...)
   .isInstanceOf(IllegalArgumentException.class).hasMessage(...)`. A
   refusal on decode plants the bad byte with `buffer.putByte` at the
   flyweights' own offset, `OFFSET + MessageHeaderEncoder.ENCODED_LENGTH
   + <Message>Encoder.<field>EncodingOffset()`, plus the member's
   `EncodingOffset()` on the composite's encoder for a member; never a
   literal offset. Another template: `new MessageHeaderEncoder()
   .wrap(buffer, OFFSET).templateId(<other>)`. A case with versions
   encodes a message, sets `headerEncoder.version(<older>)` the same way,
   decodes, and asserts the appended fields `null`; a header below the
   baseline is refused with the message the view shows. A case whose
   record can carry what the wire cannot, a string too long or not ASCII,
   an array of the wrong length, the unknown-value enum constant, a
   `null` group, asserts each refusal. The flyweight classes are in
   `corpus.<name>.sbe`, the codec beside the records.
5. **Delete the generator's case**: `git rm` the case class and remove
   its `new Case(...)` entry from `Corpus.CASES` in `Corpus.java`. Nothing
   else in the generator refers to a case by name.
6. **Build**: `./mvnw spotless:apply`, then `./mvnw verify`. A compile
   error in the schema package is the records disagreeing with the
   processor's rules, or a generated codec that does not compile, which
   is a finding: fix the product, never the oracle, and say so in the
   commit body. A failing round trip is a codec bug or a wrong value;
   read the assertion's description before deciding. `XsdCoverageTest`
   cannot fail from a move, since it reads both places.
7. **Commit**: `Move the <Name> case to the tests module`, the body
   naming what the round trips and the tests cover and any finding.
8. **When `Corpus.CASES` is empty**, in the last commit: delete
   `Corpus.java` and `CorpusTest.java`, which empties the generator's
   `corpus` test package, while `Fixtures` in the parent package stays;
   delete `SbeProcessorTest.java`, the `Corpus` overload of
   `Javac.compile` and the corpus-parameterized test of `DiscoveryTest`;
   remove the `Corpus.CASES` loop and import from `XsdCoverageTest`; tick
   14 in `intent.md`; and strike the "until every case has moved" bullet
   from `architecture.md`'s testing section. `Fixtures` keeps what
   `MappingTest` and `GeneratorTest` need and loses the rest.

## The cases to port

In this order, simplest first, so each port can copy the last. The third
column is the least the round trips and the case's own tests must show;
the case's javadoc and its `CODEC` view say the rest.

| Case | Package | Must show |
| --- | --- | --- |
| Primitives | `corpus.primitives` | Every field at zero, at its minimum and at its maximum, the unsigned ones at their top bit through the face. |
| OptionalFields | `corpus.optionalfields` | Every optional field `null`, then present; the floats' null value is `NaN`, so a float field present at `NaN` is the null value and decodes to `null`. |
| Messages | `corpus.messages` | A round trip per message; each codec refuses the other's template with `not a <Message>: schemaId ..., templateId ...`. |
| NamedTypes | `corpus.namedtypes` | The optional type's field `null` and present; the constrained values at `minValue` and `maxValue`. |
| Constants | `corpus.constants` | Each constant held; each constant given another value refused with `<component> is the constant ...`. |
| Enums | `corpus.enums` | Every value of both enums; the unknown wire value decoding to the designated constant on the enum that has one and refused with `<Enum> has no value ...` on the one that has not; encoding the designated constant refused with `<Enum>.<Constant> has no wire form`. |
| Sets | `corpus.sets` | Each set empty, with one choice and with all; a bit no choice names refused on decode with `<Set> has a bit no choice names: ...`. |
| Arrays | `corpus.arrays` | Every array full at its bounds; an array of the wrong length refused, `null` in an array field refused. |
| Layout | `corpus.layout` | The components in their declared order round tripping; a test that the unmapped field's bytes hold its null value, read through the generated decoder. |
| AddedFields | `corpus.addedfields` | A current message with every field; the same message with its header at version 1 decoding with the version 2 fields `null`; a header at version 0 refused with `... is below the baseline 1`. |
| Bindings | `corpus.bindings` | A value through every binding and back, the optional bound field `null` and present; a binding's own exception passing through unwrapped. |
| Groups | `corpus.groups` | No entries; several entries with the nested group empty in one and filled in another; the optional entry field `null`; the appended group with entries; a `null` group refused from `encodedLength` and from `encode` with `<group> is required`; a version 0 message decoding with the appended group `null`. |
| VarData | `corpus.vardata` | `codecs = false`: the oracle check only, no round trips, until increment 15. |
| Versions | `corpus.versions` | `codecs = false`: the oracle check only. |
| Header | `corpus.header` | `codecs = false`: the oracle check only, until increment 16. |
| BigEndian | `corpus.bigendian` | `codecs = false`: the oracle check only, until increment 16. |

A case named in the table does not exist by the time you read this only
if it was already ported; the generator's corpus directory is the list
of what remains.

## Criteria

- The seed's pull request: the module builds in the reactor, `Cases`
  finds `CompositesTest`, the surefire report names every check by its
  phrase, the generator's `Composites` case is gone, and `./mvnw verify`
  is green on a fresh clone with the CI job passing.
- Each port: its commit builds green, the case's round trips and tests
  run under the phrased names, the generator's case is gone, and any
  finding is fixed in the product with the fix in the same commit.
- The increment: `Corpus.CASES` is gone with everything that read it,
  every construct the old corpus covered has a case whose round trips
  cover it, `XsdCoverageTest` passes over the moved oracles alone, and
  `intent.md` ticks 14.

## Out of scope

A synthetic value generator over the IR, which the `RoundTrip` shape
would take later. Moving the example's tests, which stay where the
reference flyweights are. New constructs: var-data, byte order and header
types keep their increments and their `codecs = false` until then.
