# Increment 25: Mapping some messages of a schema

## Goal

Increment 23 made schema-first complete. Every message of the resource has
a record, every type a declaration, and a package can go back to
code-first without losing anything. That serves a schema sbe-buddy wrote
and froze. It does not serve an existing schema of two hundred messages
where the code wants records for five. Nor does it serve a team that wants
sbe-buddy only for sbe-tool's flyweights and the baseline check of
increment 24, with no records at all.

This increment lets a schema-first package map only some of the resource's
messages. It has to say so on `@SbeSchema`. What it maps is still checked
as strictly as before; what it leaves out gets flyweights and no codec.

## Settled before it started

- **Opt-in, per package.** `@SbeSchema(resource = "venue.xml", partial = true)`.
  Complete stays the default, so a package that follows increment 23's
  workflow keeps its protection against drift: a message added to its
  resource without a record is still an error. `partial` without
  `resource` is an error, since a code-first package writes exactly what
  its records say.
- **Whole messages, not parts of them.** A record that maps a message maps
  all of it: every field is a component or `unmapped`, and every group and
  var-data is a component, as in 23. A record over part of a message would
  be a view, decode-only. Views stay parked.
- **Types follow the messages.** A type the resource declares needs a
  declaration only where a mapped record reaches it. The records cannot
  reach an undeclared type anyway, since each component names its type.
  The header is always declared, as it frames every message.
- **The comparison is the same, one direction relaxed.** The rendered
  document is still held against the resource by `SchemaEquivalence`. In a
  partial package, a message or a declaration the resource has and the
  annotations lack is not a difference. Everything the annotations say
  must still be in the resource and equal to it: each mapped message whole,
  each declaration, the schema's attributes, the header.
- **No records at all is allowed.** A partial package can be a
  `package-info.java` alone, naming a resource and a baseline: sbe-tool's
  flyweights, the baseline check, and nothing else. The rendered document
  then has no message, which sbe.xsd refuses, so the comparison must
  tolerate that one gap and nothing else.
- **The baseline check holds the resource.** Schema-first,
  `SchemaEvolution` compares the document sbe-tool takes, the resource,
  with the baseline, not the rendered document, which lacks the unmapped
  messages. Complete, the two are one schema, so nothing changes there. A
  difference on a message no record maps lands on the package.
- **Going back to code-first.** A partial package cannot switch back to
  code-first without losing what it leaves out, and in code-first there is
  no resource to compare against. The guide says so. A baseline catches
  the loss: a message it has and the schema lacks is an error.

## What gets built

- **The api.** `SbeSchema.partial`, `false` by default, with Javadoc that
  says what it relaxes and what it does not.
- **The generator.** `SchemaEquivalence.differences` takes whether the
  comparison is partial and, if so, skips the resource's messages and
  declarations that have no counterpart. `Generator.generate` passes it on,
  and runs the baseline check over the resource. A rendered document with
  no message is compared on its declarations and attributes.
- **The processor.** `Discovery` reads `partial` into `Annotated`, and
  `Mapping` refuses `partial` without `resource` on the package:
  `partial maps some messages of a resource; name the resource`. In
  complete mode the error for an unmapped message gains the hint:
  `the schema has a message "Cancel" (id 2) and no record maps it; add one, or declare the package partial`.
- **The codecs** are written for the mapped messages only, as today;
  unions are over records, so they need nothing.
- **The corpus.**
  - `partialmapping`: a resource with three messages and types only some
    of them reach, and a record for one message. Its test round-trips the
    mapped message through its codec, and encodes and decodes an unmapped
    one through the flyweights.
  - `flyweightsonly`: a `package-info.java` naming a resource and a
    baseline, and nothing else. Its test uses the flyweights.
  - `SchemaRoundTripTest` leaves a partial package schema-first in both
    runs, since it cannot write its resource back, and still expects the
    same generated sources in both.
- **The snippets.** Against `venue.xml`:
  - a partial package with a record for one message compiles clean;
  - a partial package with no records compiles clean;
  - what the annotations say and the resource lacks is still an error;
  - a mapped record missing a field is still an error;
  - `partial` without a resource is an error;
  - the complete-mode error carries the hint.
- **The documents.**
  - `intent.md`: 25 is this, the API pass moves to 26, and the out-of-scope
    paragraph keeps views parked and says that messages may be left out;
  - `type-mappings.md`: the schema-first section;
  - `architecture.md`: step 3';
  - the guide: the schema-first page gains a section on mapping some
    messages, and says what going back to code-first costs;
  - README: the existing-schemas section;
  - the api's Javadoc and the modules' `AGENTS.md`.

## Criteria

- A partial package compiles with records for any subset of the resource's
  messages, none included, and gets flyweights for all of them and codecs
  for the mapped ones.
- Every difference in what the records do map is still an error on its
  node.
- A complete package behaves exactly as before, save the hint.
- `./mvnw verify` is green.

## Out of scope

Views: records over part of a message, decode-only. Mapping a message twice.
Choosing which messages get flyweights: sbe-tool generates them all.
