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
byte order, custom header types. Plus message families as sealed interfaces
with a dispatching codec, the one Java-side feature. The target Java
representation of all of it is `type-mappings.md`.

## Out of scope

RPC (`rpc.md`), Aeron transport, typed flyweights, tooling over sbe-tool's
schema corpus. None of these appears in code until
the scope above is complete, and nothing built may preclude them.

## Non-negotiables

1. **Byte-identical to sbe-tool.** sbe-buddy writes the XML schema; sbe-tool
   parses it, builds the IR and generates the flyweights, so the bytes are
   sbe-tool's by construction. The hand-written XML next to the example
   records is the oracle: the written schema must equal it; it grows with the
   records in the same change and is never edited to make a test pass.
2. **Nothing is inferred.** Schema id, version, template ids, field ids,
   unsigned types, named types, `sinceVersion`: all written by hand in
   annotations. The only default is the same-width signed primitive for a
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

- [x] 1. Build
- [ ] 2. The schema model and its XML, complete, with the corpus
- [ ] 3. Annotations and the processor; the example and its oracle
- [ ] 4. sbe-tool in the pipeline: flyweights, the schema in the jar, the backstop
- [ ] 5. Codec: primitives
- [ ] 6. Codec: schema evolution
- [ ] 7. Codec: enums
- [ ] 8. Codec: named types and bindings
- [ ] 9. Codec: composites
- [ ] 10. Codec: fixed-length arrays
- [ ] 11. Codec: groups
- [ ] 12. Codec: var-data
- [ ] 13. Codec: sets, constants, optional presence
- [ ] 14. Codec: byte order and header types
- [ ] 15. Message families
