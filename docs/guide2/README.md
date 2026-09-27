# sbe-buddy guide

sbe-buddy describes SBE schemas with annotated Java records. It generates
the schema XML, standard SBE flyweights, and codecs that convert between
encoded messages and your records. You can also map an existing XML schema.

This guide is for Java developers who have used SBE and want to work with
records in their applications. It explains the relevant SBE rules as they
arise. The [glossary](../glossary.md) defines the terms used throughout.

## Tutorials

- [Encode and decode your first message](tutorials/getting-started.md) —
  create a Maven project, inspect its generated schema, and run a complete
  round trip.

## How-to guides

- [Encode and read multiple messages](how-to/use-codecs-in-an-application.md)
  — size buffers, use offsets, reuse codecs and dispatch message types.
- [Add a field while supporting older messages](how-to/add-a-field.md) —
  extend a message and check readers in both directions.
- [Retire a field from a record](how-to/retire-a-field.md) — remove a Java
  component while preserving the field's place in the schema.
- [Map an existing XML schema](how-to/map-an-existing-schema.md) — declare
  matching Java records and check them against the schema during compilation.
- [Use BigDecimal for a price](how-to/use-bigdecimal-for-a-price.md) — keep
  a mantissa-and-exponent schema while using decimal values in your message
  record.

## Reference

- [Fields](reference/fields.md) — primitive mappings, unsigned values,
  presence and fixed-block layout.
- [Named types, strings and arrays](reference/named-types.md) — reusable
  encodings, fixed lengths and constants.
- [Enums](reference/enums.md) — explicit values and unknown-value handling.
- [Sets](reference/sets.md) — bit choices and Java sets.
- [Composites](reference/composites.md) — records for structured values,
  inline members and references.
- [Repeating groups](reference/groups.md) — lists of entries, dimensions
  and nested bodies.
- [Variable-length data](reference/variable-length-data.md) — text and
  binary payloads with length prefixes.
- [Bindings](reference/bindings.md) — binding interfaces, supported
  declarations, and how conversion interacts with absence and validation.

## Concepts

- [How an SBE message is laid out](concepts/message-layout.md) — headers,
  fixed-length blocks, repeating groups and variable-length data, and their
  Java representations.
- [Schema versions and absence](concepts/schema-versions-and-absence.md) —
  current and acting versions, the baseline, and compatibility limits.
- [Code-first and schema-first](concepts/code-first-and-schema-first.md) —
  choosing where the schema comes from and understanding what the compiler
  checks.
- [Application types and wire representations](concepts/application-types-and-wire-representations.md)
  — how a binding connects the types your application uses to the types
  defined by its schema, and when that extra conversion is useful.
