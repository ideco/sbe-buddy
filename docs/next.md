# Increment 24: A checked-in baseline

## Goal

`baselineVersion` states a number: the oldest version the codecs read.
Nothing checks that the schema still reads that version. A refactor that
reorders two fields, retypes one or drops a value from an enum compiles
clean, and the first to notice is a reader on the other side. That is the
real risk of writing a schema from code.

This increment replaces the number with the schema itself. `@SbeSchema`
names a checked-in XML file, the version the schema must stay compatible
with, and the compiler holds the schema against it. The baseline version
the codecs read from becomes that file's `version`. A change that breaks
the baseline is a compile error on the node it is on. A change SBE allows,
an appended field, a renamed one or a new enum value, compiles.

## Settled before it started

- **XML against XML.** The check compares the schema's document with the
  baseline's, both parsed with sbe.xsd so the defaults are filled, as
  `SchemaEquivalence` does, and reports on the same paths. It does not use
  sbe-tool's IR, which blurs since-versions and semantic types
  (`notes.md`). It lives next to `SchemaEquivalence` and shares its parsing
  and its `Difference`. In schema-first mode it runs after the equivalence
  check, over the same rendered document, so it works in both modes.
- **Names are free.** The spec lists name and description corrections as
  changes that keep compatibility, and sbe-tool matches nothing across
  versions by name. So messages match by template `id`; fields, groups and
  var-data by position; types by what they are, following each `type`
  reference into its own document. `name`, `description` and `deprecated`
  are never compared. Everything else is either the wire or what a value on
  it means, and stays.
- **Ids are identity.** A field's or a group's id is not on the wire, but
  it is the field's FIX tag, "the same type" wherever it appears. Only the
  id tells a rename, same position and same id, from a replacement, same
  position and type but a new id. A replacement reads cleanly on the wire
  and means something else to an old reader. A changed id at a position is
  an error. A field that means something else is a new field.
- **Presence may change between required and optional.** Neither changes
  the wire. A change to or from `constant` does, because a constant takes
  no space in the block.
- **The spec's constraints, nothing more.** Fields are appended to the end
  of a block, groups after groups, var-data after var-data; an existing
  field keeps its type and its place; the header does not change. Added to
  that, from the spec's `sinceVersion`: an addition states its version,
  above the baseline's, and an existing element's version never changes.
  An enum value and a set choice may be added, as the spec's example adds
  `OrdType=J`. A composite never grows, as `type-mappings.md` already
  requires. Nothing is removed: a field is retired with `unmapped`, a
  message or a value with `deprecated`.
- **Block lengths and offsets stay as stated.** An explicit `offset` must
  equal the baseline's, and so must its absence. A `blockLength` may grow,
  but not shrink, appear or disappear. The offsets that are computed follow
  from equal types in equal order, so nothing here computes a layout.
- **One baseline.** It guards against breaking the version it names, not
  the latest release. A history of released versions, each checked, is a
  later increment if it earns one. So are generated sources in `src`.
- **No baseline** means what `baselineVersion = 0` meant: the codecs read
  from version 0, and nothing is compared.

## What gets built

- **The api.** `@SbeSchema(baseline = "orders-v3.xml")` replaces
  `baselineVersion`. It is a class path name resolved as `resource` is,
  relative to the package or absolute with a leading slash.
  `baselineVersion` goes; the api is pre-1.0 and has one user.
- **The processor** reads the baseline through `Filer.getResource` on
  `CLASS_PATH`, as it reads a resource, and hands the text and its URI to
  the generator. A missing file is an error on the package:
  `no baseline mistakes/nowhere.xml on the class path`.
- **The generator.** `SchemaEvolution.differences(schema, baseline)`,
  beside `SchemaEquivalence`, with paths in the schema's names so each
  difference lands on its node through `Generator.node`. A baseline node
  with no counterpart lands on its nearest parent: a removed field on its
  message, a removed message on the package. `Generator.generate` takes the
  baseline, resolves its XIncludes, and runs the comparison before sbe-tool
  sees the document. With no difference, the baseline's version goes to
  the codec walk. `Mapping` loses its baseline and its range check, and
  `Annotated` carries the baseline's name.
- **The rules, each a difference:**
  - the schema: the same `id` and `byteOrder`, a `version` at or above the
    baseline's, and the header the same structure;
  - a message the baseline has, matched by `id`, is still there; a message
    it lacks has a `sinceVersion` above the baseline's version;
  - in each message and group, the baseline's fields, groups and var-data
    come first, in order, each with its `id`, its `sinceVersion`, its
    `offset`, its `semanticType`, `epoch`, `timeUnit`, a constant's value,
    and its type; what follows has a `sinceVersion` above the baseline's
    version; `blockLength` only grows;
  - a type is compared as what it is, not by name: a primitive, or a
    `type`'s primitive, length, encoding, bounds, null value and
    constant; a composite member for member in order; an enum's encoding
    and its values, matched by value; a set's encoding and its choices,
    matched by bit. An enum value or choice the baseline lacks has a
    `sinceVersion` above the baseline's version, and one it has stays.

  The errors read as the fix. Examples:
  `the baseline has id 44 here, not 99; a field that means something else is a new field, appended`,
  `the baseline's Order has a field "price" (id 5) the schema lacks; a field stays, unmapped if the record retires it`,
  `a field the baseline lacks needs a sinceVersion above the baseline's version 1`.
- **The codecs** are unchanged: a message below the baseline is refused,
  and a field at or below it may be a primitive.
- **The corpus.** `addedfields` checks in its version 1 schema at
  `src/main/resources/corpus/addedfields/addedfields-v1.xml` and names it.
  `SchemaRoundTripTest` puts the modules' resources on the class path of
  the code-first run too, so a baseline is found in both.
- **The example.** `quotes` names its frozen `quotes-v1.xml`, which moves
  unchanged to `src/main/resources/com/example/quotes/`, where a user's
  baseline lives. The pom's reference execution reads it from there.
- **The snippets.** One per rule, against `order-v1.xml` beside the test:
  each breaking change as an error on its line, and the allowed ones, a
  rename, a presence change, an appended field and a new enum value,
  compiling clean. The snippets on absence above and at the baseline move
  onto the file, and the one on a baseline above the schema's version
  becomes the file's.
- **The documents.** `intent.md` makes 24 this and moves the API pass to 25;
  `type-mappings.md`'s evolution sections; the guide's schema page and the
  versioning passages that name `baselineVersion`; the README's versioning
  paragraph; the api's Javadoc; the generator's and example's `AGENTS.md`.

## Criteria

- Each rule above is an error on the node it concerns, and each allowed
  change compiles clean.
- The example and the corpus compile against their baselines, and their
  codecs refuse below them as before.
- `./mvnw verify` is green.

## Out of scope

A release history, and checking against the latest release rather than one
baseline. Writing generated sources or the schema into `src`. Views.
Comparing layouts computed from sizes; stated offsets and block lengths are
compared as stated.
