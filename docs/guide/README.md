# Guide

## Getting started

- [Getting started](getting-started.md) — a first message, end to end

## Reference

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
