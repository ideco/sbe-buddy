# Increment 9: the layout, and fields the record does not carry

## Goal

A record may say its wire order at the top, `@SbeMessage(layout = {...})`,
over its components and over fields it does not carry,
`unmapped = {@SbeField(...)}`. Both are optional and default to nothing, so
every schema written so far maps exactly as before. Two things become
possible. A field SBE can never remove, deprecated or merely useless to
this program, leaves the record: the codec writes its null value and skips
it on the way back, and the wire is unchanged. And the order of a record's
components stops mattering: the layout is a named list an IDE will not
shuffle, where component order is one keystroke away from a silent change
of every offset. Nothing is inferred, because the layout is written by hand
and every unmapped field is declared in full; nothing in the schema changes,
because both members are the Java side. The increment comes before more
constructs so that each of them lands on a message shape that is final.

## Settled before it started

- sbe-tool's fixed-block accessors are offset-addressed,
  `buffer.getInt(offset + 24, ...)` in the generated quotes decoder, so a
  codec may read and write the fields of a block in any order; only groups
  and var-data are sequential. An annotation may carry an array of
  annotations as a member, and only self-nesting is forbidden;
  `Discovery` reads members from the `AnnotationMirror`, where an array
  of annotations is a list of mirrors. All in `notes.md`.

## What gets built

- **Two members on `@SbeMessage` and on `@SbeGroup`**, the Java side,
  contributing nothing to the schema: `String[] layout() default {}`, the
  body's components and unmapped fields in wire order, by name; and
  `SbeField[] unmapped() default {}`, fields of the body that no component
  carries, each a complete `@SbeField`. On `@SbeGroup` they describe the
  group's record, the way the group's members already do.
- **`Annotated`.** `Message` and `Group` gain `layout`, a list of names,
  and `unmapped`, a list of `Annotated.Field` whose `javaType` is the new
  `JavaType.Unmapped` and whose `javaName` is its wire `name`. `Discovery`
  reads both arrays; each unmapped field is read as a component's
  `@SbeField` is, without a Java type; the corpus DSL gains `layout` and
  `unmapped` on its message and group builders and `unmapped()` as a Java
  type.
- **The rules, in `Mapping`,** decidable from the message or group node
  and blamed on it, with the `@SbeMessage` or `@SbeGroup` mirror, because
  an unmapped field has no element of its own:
  - an unmapped field gives its `name`, and its `type` or
    `primitiveType`, since nothing can default them: `an unmapped field
    needs a name` and `an unmapped field needs a type or a primitiveType`;
  - a body with unmapped fields gives a `layout`: `unmapped fields need a
    layout to take their place in`;
  - a `layout` names every component by its Java name exactly once and
    every unmapped field by its `name` exactly once, and nothing else: `the
    layout misses quantity`, `the layout names price twice`, `the layout
    names nothing called prize`; a name that is both a component's Java
    name and an unmapped field's `name` is a `Problem` too;
  - the face and boxing rules skip an unmapped field, which has no
    component, and a constant unmapped field is as legal as a constant
    field.

  The body's order is the `layout` when given and declaration order
  otherwise; the fields-then-groups-then-data rule and, in
  `Generator.validate`, the append-only rule and the name and id checks
  run over that order, so an unmapped field with a `sinceVersion` obeys
  them like any other. `Schema` and `SchemaXml` do not change.
- **The codec emitter.** An unmapped field is written as its null value
  on encode, `encodeUnmappedField` for a primitive,
  `encoder.<field>(<Msg>Encoder.<field>NullValue())`,
  `encodeUnmappedEnumField` writing `NULL_VAL`, and
  `encodeUnmappedSetField`, `encoder.<field>().clear()`; a constant needs
  nothing. On decode it contributes nothing. The constructor call's
  arguments follow the record's component order, which is what the
  canonical constructor takes, while the encode statements follow the
  wire order the emitter already walks; the two agree today because they
  are the same order and diverge under a `layout`, which fixed-block
  addressing makes harmless. The emitter finds a field's component by
  wire name as before and, finding none, its unmapped declaration.
- **The corpus.** A new case, `Layout`, a message whose record lists its
  components out of wire order and whose layout retires a deprecated
  field in the middle of the block; its oracle is the document a record
  in wire order would write, which is the point, and its codec view
  shows the null value written, the field skipped, and the constructor
  in component order. The `Groups` case gives one group a `layout` with
  its record's components out of order, which changes no oracle. A
  `Layout` twin without the `unmapped` field and a group without a
  `layout` keep proving the default is untouched, since every other case
  is one.
- **The example.** `com.example.quotes` goes to `version = 4` and retires
  `tradeCount`, which the feed now sends elsewhere: the field stays in
  the schema with `deprecated = 4`, moves from the record to `unmapped`,
  and the record states its `layout`. `quotes-v3.xml` is frozen with its
  reference package `xmlref.v3`. The tests: a round trip without the
  component; the reference decoder reading the null value of `uint32`
  where `tradeCount` was and every other field as written; a version 3
  message with a `tradeCount` decoding to the same record without it; a
  version 3 reader reading a current message whole.
- **The guide.** A how-to page, `docs/guide/how-to/retire-a-field.md`,
  beside the planned "Evolve a message": deprecate the field, move it to
  `unmapped`, state the `layout`, and what the wire carries afterwards;
  the guide's index links it. The primitives page's sentence that fields
  appear in component order gains its qualification, with a pointer to
  the how-to. The schemas reference page, when it is written, documents
  both members with `@SbeMessage`.
- **The documents.** `type-mappings.md` names the two members on the
  `message` and `group` rows as the Java side, and its layout section says
  the order is the `layout` when given; `architecture.md`'s models
  section takes the new components and the Java type, its rules section
  the new rules, its generation section the unmapped shapes and the two
  orders; `notes.md` takes the facts above; `intent.md` ticks increment
  9, which this pull request already inserted before the constructs that
  remain.

## Criteria

- `Layout`'s emitted codec equals its `codecs` view exactly, and every
  existing case's oracle and codec view are untouched.
- The quotes example round trips without `tradeCount`, writes the null
  value where it was, and decodes a version 3 message that carries one,
  all in the real build.
- Every rule above has a test that builds the mistake and asserts the
  `Problem` and the node, and one processor snippet asserts a layout
  mistake lands on the record's `@SbeMessage`.
- The guide's how-to compiles as written: its snippet is the example's.
- `./mvnw verify` is green on a fresh clone, and the CI job passes on this
  pull request.

## Out of scope

Unmapped groups and var-data, which must still be walked on decode and
written empty on encode; they belong with evolution, increment 16. A
written value other than the null value, which would need a wrapper
around `@SbeField` and a need that has not appeared. `layout` and
`unmapped` on `@SbeComposite`, which meet the same footgun and arrive
with composites, increment 12. Reordering that changes the wire: the
layout says where fields are, never moves them.
