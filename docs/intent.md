# sbe-buddy

Code-first SBE for Java. You write the domain record; sbe-buddy makes it the
schema.

```java
@SbeSchema(id = 1, version = 0)
package com.example.trading;

@SbeMessage(id = 1)
public record PlaceOrder(
    @SbeField(id = 1) long accountId,
    @SbeField(id = 2) int quantity) {}
```

Compiling it runs a javac annotation processor that writes the equivalent SBE
XML schema from the records and hands it to sbe-tool, which parses it, builds
the IR and generates the usual flyweights in `com.example.trading.sbe`; the
processor then generates a `PlaceOrderCodec` between the record and those
flyweights. The schema ships in the jar, so sbe-tool's other generators
produce the same messages for C, C++, C# and Rust. The wire format is exactly
what sbe-tool produces from the equivalent XML schema, and the project proves
that continuously against a hand-written XML oracle.

## Scope

Mapping, codecs and sbe-tool's flyweights, until every feature `sbe.xsd` can
express is expressible as annotated Java with identical IR and bytes:
primitives, unsigned and `char`, enums, sets, named types, composites,
fixed-length arrays, groups, var-data, constants, optional presence, schema
evolution (`sinceVersion`, `deprecated`, acting version, `semanticVersion`),
byte order, custom header types. Plus unions of messages as sealed interfaces
with a dispatching codec, the one Java-side feature. Then what makes the
codec worth using: a binding on any component, told what the schema says
about it, so a record holds its own types over any wire the schema declares,
and a realistic FIX-like order-entry schema as the proof that the whole
thing holds.
sbe-buddy stays agnostic of FIX: `sbe.xsd` and sbe-tool are the contract,
and FIX's datatypes are a user's schema like any other. The target Java representation of all of it is
`type-mappings.md`.

## Out of scope

RPC (`rpc.md`), Aeron transport, typed flyweights, tooling over sbe-tool's
schema corpus. Merging schemas: a package's schema is written from its
annotations or read from a resource, never assembled from both. Views:
records over part of a schema someone else owns, decode-only, which is
what mapping a venue's published schema would need; schema-first maps the
whole schema or nothing, so that a package can go back to writing it.
sbe-tool's own `JavaDtoGenerator` is the nearest prior art
to the codec: it generates its own DTO classes from the IR, where sbe-buddy
maps to the user's records, with bindings, `null` for absence and
unions. None of these appears in code until
the scope above is complete, and nothing built may preclude them.

## Non-negotiables

1. **Byte-identical to sbe-tool.** sbe-buddy writes the XML schema; sbe-tool
   parses it, builds the IR and generates the flyweights, so the bytes are
   sbe-tool's by construction. The hand-written XML next to each example
   schema is its oracle: the written schema must equal it; it grows with the
   records in the same change and is never edited to make a test pass.
2. **Nothing is inferred.** Schema id, version, template ids, field ids,
   unsigned types, named types, `sinceVersion`, the baseline a
   schema stays compatible with: all written by hand in annotations. The only default is the same-width signed primitive for a
   bare Java primitive.
3. **Annotations mirror `sbe.xsd`.** One annotation per XSD element, one
   member per attribute, same names and defaults, on the node the XSD puts it
   on. sbe-tool's own `sbe.xsd` and its `xml` parser are the reference, never
   memory. The Java side adds only what SBE has no model for, and none of it
   contributes to the schema.
4. **Absence is `null`.** A field that can be missing on the wire, because it
   is `presence="optional"` or was added in a later version than the message
   being decoded, is a nullable component: boxed for primitives, `null` for
   enums, composites and bound types. The codec maps SBE's null value to
   `null` and back. Encoding `null` into a required field is an
   `IllegalArgumentException`.
5. **Evolution is built in, not bolted on.** Every model node carries
   `sinceVersion` and `deprecated` from the first increment. Fields added in a
   later version must follow every field of earlier versions, in the message
   and in each group; the compiler rejects anything else, which sbe-tool does
   not. Decoding reads the acting version and block length from the header;
   encoding always writes the current version. An old decoder skips fields a
   newer version appended to the block; it cannot skip appended groups or
   var-data, which is SBE's limit, not ours.
6. **Length before encoding.** `Codec.encodedLength(value)` returns the exact
   number of bytes `encode` will write, header included, so a caller can
   `tryClaim` first. Every test asserts the two agree.
7. **Agrona is the only runtime dependency.** The processor and sbe-tool sit
   on the processor path only and never reach a user's compile classpath.

## Increments

In phases, in order; the increments are the fine print, and `next.md` holds
the one being built.

**Foundations.** The pipeline end to end, sbe-tool's flyweights out, and
the codec on the smallest case so its mechanics settle before they grow.

* [x] 1. Build
* [x] 2. The schema model and its XML, complete, with the corpus
* [x] 3. The annotations, and the mapping from them to the model; no javac
* [x] 4. Discovery and the processor; the schema in the jar; the corpus's source view; the example
* [x] 5. sbe-tool in the pipeline: the backstop and the flyweights. The first release, `v0.1.0`: records in, sbe-tool's flyweights out
* [x] 6. Codec: primitives

**Simple messages.** Fixed-length blocks, no nesting: every construct of
the block, one at a time.

* [x] 7. Codec: absence. Optional presence and `sinceVersion` on fields, boxed components, the acting version read in decode; the first frozen schema version and the cross-version tests against reference flyweights
* [x] 8. Codec: enums and sets, under the unknown-value contract of `type-mappings.md`
* [x] 9. The layout: `layout` and `unmapped` on `@SbeMessage` and `@SbeGroup`, the wire order named at the top of the record and fields the record does not carry, so a record can retire a field and its component order stops mattering
* [x] 10. Codec: named types, constants, fixed-length arrays and `char` strings
* [x] 11. Codec: bindings. `TypeBinding` and its primitive specializations, `binding` on `@SbeField`, over a primitive's, a string's and an array's face; a declaration is never a binding

**Composites.** Structured values, and bindings over them.

* [x] 12. Codec: composites. Nested records, `@SbeRef`, inline declarations, `layout` and `unmapped` on the composite; a binding over a composite face goes through the face record

**Variable length.** `encodedLength` has to earn its keep.

* [x] 13. Codec: groups, nested
* [x] 14. The tests module: the corpus compiled by the real build, one schema case per package run against the generated flyweights and codecs; the generator's twins and codec views retire as each case moves
* [x] 15. The codec model: a walk from the IR and the annotations to a small model of each codec, its grammar in one file, and a writer that renders it through the templates
* [x] 16. Codec: var-data, and the built-in var-data encodings

**The rest of `sbe.xsd`.**

* [x] 17. Codec: byte order and header types

**Evolution, proved.** Through the codecs, not only the flyweights.

* [x] 18. Evolution through every construct: `sinceVersion` inside groups and composites, var-data appended inside a group included, every frozen version decoded in both directions

**Unions.**

* [x] 19. Unions: `@SbeUnion` on a sealed interface and its dispatching codec, composed from the message codecs; `canDecode` on every codec

**Bindings everywhere.** Built on the normal SBE model, no special cases;
the wire stays what the schema declares, and what a value means is the
user's binding's to decide. No built-in bindings.

* [x] 20. A binding on every component that carries a value, enums, sets, var-data, groups and composite members included, each call handed a `BindingContext` from the schema; `timeUnit` deprecated as `sbe.xsd` has it; text in any encoding the JDK knows; `UuidWire` out of the api

**A real schema.**

* [x] 21. A FIX-like order-entry schema, the example's `trading` grown into the showcase: FIX tags as ids and FIX's shapes without being FIX, every construct where such a schema uses it and the bindings beside it, unions per direction and over both, byte-compatible with sbe-tool's flyweights from its hand-written oracle in both directions; an optional composite, set or array field, its null a binding's

**Schema-first.**

* [x] 22. Schema-first mapping: `@SbeSchema` names an XML resource on the class path, sbe-tool generates every message's flyweights from it, and the records map the messages they choose to with the same annotations, each written member checked against the XML; nothing is written. First the join: the face rules run over the IR where each token meets its annotation, so the pipeline is one flow from the document down
* [x] 23. Schema-first without drift: the annotations describe the whole document, every message, member and type, and the compiler proves the document rendered from them and the resource are one schema, each difference an error on the node it is on; partial mapping goes, and views are parked. A package goes code-first, freezes its schema, reads it, and back, losing nothing either way

**Evolution, checked.**

* [x] 24. A checked-in baseline: `@SbeSchema(baseline = …)` names the released schema's XML in place of `baselineVersion`, the compiler holds the schema against it under SBE's extension rules, XML against XML, messages by id, members by position and id, types by structure, names free, and the codecs read from its version

**The API pass.**

* [ ] 25. Whatever feels awkward in the annotations and the codec once 21 works: names, `Problem` wording, javadoc, what the api exports. The running example in `type-mappings.md` compiles verbatim
