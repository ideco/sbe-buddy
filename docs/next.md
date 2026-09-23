# Increment 18: Evolution through every construct

## Goal

A schema evolves the ways SBE allows, and the codec reads every earlier
version of it. Today the codec refuses anything appended inside a group, and
reads a member appended to a composite from older messages without a guard.
This increment follows SBE's own rules: what the standard lets a schema
append, the codec reads from older and newer messages alike; what it forbids
is refused by the compiler with the official way instead.

## What SBE allows

From the FIX standard's schema extension mechanism and sbe-tool's versioning
guide:

- **Fields** may be appended to the end of a message's root block or of a
  group's block. The header's and the dimension's block lengths let a
  reader of either version step over the difference.
- **A group** may be appended after the existing groups, at the root or
  nested within a group; **var-data** after the existing var-data, at the
  root or within a group.
- **A composite cannot be extended**: "It is not possible to add fields to a
  composite type without creating a new message template and schema
  version." The official way is a new composite, carried by a new field.
- **The header's encoding cannot change.**

## What the spike showed

- **Appending inside a group almost works.** With the codec's three
  refusals lifted, a field, a nested group and var-data appended inside a
  group's entry compiled and round-tripped, and a hand-built version 0
  message decoded to `null` for all three with the right lengths. sbe-tool's
  group decoders guard every appended member by the message's acting
  version, which the codec's added-member templates already read.
- **An appended composite member is read from bytes it does not own.**
  sbe-tool puts no version guard inside a composite (`JavaGenerator.java`,
  `generateFieldNotPresentCondition`), and the walk never marks a
  composite's member as added, so a member appended in version 1 read
  `0x5A5A5A5A` from a version 0 message: the bytes after its block. The
  compiler accepted an `int` for it.
- **An older reader cannot skip a group or var-data appended inside an
  entry.** sbe-tool's `next()` starts an entry at the message's limit
  (`JavaGenerator.java`, the group decoder's `next`), and an older reader
  never reads the unknown nested part, so it reads that as the next entry.
  The standard allows the append; older sbe-tool readers of a group with
  more than one entry, or with anything after it, do not survive it.
  Appending fields to an entry is safe both ways.

## Settled before it started

- **Inside a group, everything SBE allows.** The codec's refusals of a
  field, a group and var-data appended above the baseline inside a group
  go. Each decodes to `null` from a message older than it, decided on the
  acting version, and the compiler asks for a box on a field that can be
  absent, as it already does.
- **A composite's member is never newer than its composite.** A member,
  inline or a ref, whose `sinceVersion` is above its composite's own is
  refused by `Mapping`: `a composite cannot be extended in a later version;
  declare a new composite and append a field of it`. The composite's own
  `sinceVersion`, the version the whole type arrived in, stays as it is.
- **A header's member is never versioned.** A header member with a
  `sinceVersion` is refused by `Mapping`: `a header cannot change: a reader
  needs its length before its version`.
- **The older-reader limit is documented, not warned about.** The guide
  recommends appending fields to an entry, and says what a nested group or
  var-data appended inside an entry costs readers of the older version.

## What gets built

- **`CodecWalk`.** The three refusals inside a group go.
- **`Mapping`.** The two rules, each on the member it names.
- **The corpus.** One schema at three versions, each its own package with
  its own records, codecs and oracle, so every version reads every other
  through our codecs:
  - `evolution.v0`: a message whose group's entry holds a field and a
    composite.
  - `evolution.v1`: the entry appends a field after the composite, and the
    nested entries of a group already there append one too.
  - `evolution`, version 2: the entry appends a nested group and var-data.
  - Their tests cross: v0 and v1 each read the other's messages; the
    current version reads v0's and v1's, with every appended member `null`;
    lengths agree in every direction the readers support.
- **The snippets.** A composite member newer than its composite, a ref
  newer than its composite, a versioned header member.
- **The example.** `quotes` version 9 appends a field to the contributors
  entry, freezing `quotes-v8.xml` with its reference flyweights. The
  reference tests cross versions 8 and 9 both ways: sbe-tool's version 8
  reader steps over the appended field by the entry's block length, and our
  codec reads version 8's entries with the field `null`.
- **The documents.** `type-mappings.md`'s evolution rules; the guide's
  groups, composites and headers pages, and the stale line of the
  retire-a-field how-to about composites without a layout; `notes.md` on
  the group decoder's `next` and the composite without a guard; `intent.md`
  ticks 18.

## Criteria

- Every appended member of every construct SBE lets grow decodes from every
  older version of the corpus's schema, and every version a reader supports
  crosses both ways with its lengths.
- The quotes reference tests cross versions 8 and 9 in both directions.
- Nothing a composite or a header gains in a later version compiles.
- `./mvnw verify` is green on a fresh clone, and the CI job passes on this
  pull request.

## Out of scope

A new message template as the way to replace a message wholesale, which
families in increment 19 give a home. Deprecation beyond the attribute. A
set choice or enum value added in a later version stays under the
unknown-value contract of `type-mappings.md`.
