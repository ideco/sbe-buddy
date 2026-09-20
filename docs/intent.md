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

Compiling it runs a javac annotation processor that builds SBE's intermediate
representation from the record, runs SBE's own `JavaGenerator` to produce the
usual flyweights in `com.example.trading.sbe`, and generates a
`PlaceOrderCodec` between the record and those flyweights. The wire format is
exactly what sbe-tool produces from the equivalent XML schema, and the project
proves that continuously against a hand-written XML oracle.

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

RPC (`rpc.md`), Aeron transport, typed flyweights, emitting the XML schema,
tooling over sbe-tool's schema corpus. None of these appears in code until
the scope above is complete, and nothing built may preclude them.

## Non-negotiables

1. **Byte-identical to sbe-tool.** Same IR, same `JavaGenerator`, same
   bytes. The hand-written XML next to the example records is the oracle; it
   grows with the records in the same change and is never edited to make a
   test pass.
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

- [ ] 1. The build: modules, formatter, nullness check, CI, reference checkout
- [ ] 2. The example and its oracle: `trading.xml`, sbe-tool's reference flyweights, the first byte test
- [ ] 3. Model and IR: the example records give the same IR as `trading.xml`
- [ ] 4. Flyweights and codec: `PlaceOrder` round-trips, same bytes as the reference, cross-decodes, `encodedLength` exact
- [ ] 5. Evolution: version 1 adds a field with `sinceVersion = 1`; nullable components; the append-only rule; cross-version tests against a frozen `trading-v0.xml`
- [ ] 6. Unsigned, `char`, enums
- [ ] 7. Named types, bindings, composites
- [ ] 8. Fixed-length arrays
- [ ] 9. Groups, including nested
- [ ] 10. Var-data
- [ ] 11. Sets, constants, optional presence, `deprecated`, big-endian, custom header type
- [ ] 12. Sealed families and dispatch codecs
