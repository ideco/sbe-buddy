# Guide

How to describe an SBE schema as annotated Java, and what the generated schema, flyweights and codecs do with it.

The guide is split by what you came for: a first message end to end, one page per SBE construct, a recipe for a particular task, and the reasoning behind a design decision. Each reference page stands on its own, so the one for the construct you are writing is the one to open.

For what sbe-buddy is, why it exists and how to put it on the compile path, see the [project README](../../README.md). For the Java form of every node of `sbe.xsd`, stated normatively, see [type mappings](../type-mappings.md); the guide follows it.

Entries below without a link are not written yet.

## Getting started

- Getting started — a first message, end to end

## Reference

One page per SBE construct: what to declare, the schema it produces, and how the codec behaves.

- Schemas — schema and message declarations
- [Primitives](reference/primitives.md) — scalar primitive fields, their Java types and absence
- [Enums](reference/enums.md) — `@SbeEnum`, explicit wire values and unknown values
- [Sets](reference/sets.md) — `@SbeSet`, bit choices and `Set` components
- Composites — composites and refs
- Groups — repeating groups
- Variable data — strings and byte arrays
- Codecs — the `Codec` contract and its lifecycle

## How-to

- Evolve a message — add a field without breaking existing readers
- Use flyweights directly — work on the buffer where the record is not the right fit

## Concepts

- Records and flyweights — what each layer is for, and when to reach past the codec
- Schema evolution — versions, the baseline, and what absence means

## Beyond the guide

- [Coverage](../coverage.md) — what the schema and the codec support today
- [Type mappings](../type-mappings.md) — the normative Java form of every schema node
