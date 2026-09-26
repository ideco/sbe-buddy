# Guide

sbe-buddy maps annotated Java to an SBE schema, uses sbe-tool to generate the standard flyweights, and generates codecs between those flyweights and the Java model.

Start with [Getting started](getting-started.md) for a complete example from declaration to encode/decode.

## Reference

* [Schemas](reference/schemas.md) — `@SbeSchema`, its attributes and byte order
* [Schema-first](reference/schema-first.md) — freezing a schema and reading it back: `resource`, what is checked, the build
* [Headers](reference/headers.md) — the message header, a header of your own, reading it first and passing a message on
* [Primitives](reference/primitives.md) — scalar primitive fields, their Java types and absence
* [Named types](reference/named-types.md) — `@SbeType`, fixed-length strings and arrays, constants
* [Bindings](reference/bindings.md) — `TypeBinding`, `binding` on any component and the `BindingContext` it is handed, a record's own types over the wire's faces
* [Enums](reference/enums.md) — `@SbeEnum`, explicit wire values and unknown values
* [Sets](reference/sets.md) — `@SbeSet`, bit choices and `Set` components
* [Composites](reference/composites.md) — `@SbeComposite`, members, refs, the record as the field
* [Groups](reference/groups.md) — `@SbeGroup`, the entry record, nested groups, what a group costs
* [Variable data](reference/var-data.md) — `@SbeData`, strings and byte arrays after the block
* [Unions](reference/unions.md) — `@SbeUnion`, one codec over several messages, the exhaustive `switch`, replacing a message, routing with `canDecode`
* [Typed flyweights](reference/flyweights.md) — every message as a sequence of stages over sbe-tool's flyweights, in wire order
* Codecs — the `Codec` contract and its lifecycle

## How-to

* Evolve a message — add a field without breaking existing readers
* [Retire a field](how-to/retire-a-field.md) — deprecate it, drop it from the record, keep the wire layout
* Use flyweights directly — work on the buffer where the record is not the right fit

## Concepts

* Records and flyweights — what each layer is for, and when to reach past the codec
* Schema evolution — versions, the baseline, and what absence means
