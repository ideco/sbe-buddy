# Increment 23: Schema-first, without drift

## Goal

Increment 22 let a package read its schema from a resource and map part of
it. This increment narrows it to what a shipped schema needs and makes it
safe: the compiler proves that the annotations and the document are the
same schema. The workflow it serves: develop code-first, freeze the schema
sbe-buddy wrote as a resource and switch the package to reading it, later
switch back to grow it. The switch is one member on `@SbeSchema` in either
direction, and neither direction loses anything, because the annotations
always describe the whole document and the document always says what the
annotations say. Drift, in either direction, is a compile error naming the
node that differs.

## Settled before it started

- **Not partial.** Every message of the document has a record, every field
  of a message is a component or an `unmapped` entry, every group and
  var-data is a component, every type of the document has its declaration,
  and nothing in the annotations is absent from the document. Anything
  less is drift the moment the package goes back to code-first. Mapping
  part of a schema someone else owns is a different feature, views over a
  schema, decode-only; `intent.md` parks it.
- **The annotations are complete.** They must be able to write the schema
  again, so a member the document decides and the annotation leaves out is
  gone. `@SbeField(id = 11) String clOrdId` alone is refused as it is
  code-first; the type is named. On the day the package switches, the
  annotations are the ones that wrote the document, so nothing changes.
- **The check is equivalence, not a per-node join.** Schema-first runs the
  code-first pipeline up to the document: `Mapping`, `Generator.validate`,
  `SchemaXml`. The rendered document is then compared with the resource,
  after XIncludes, with `sbe.xsd`'s defaults filled on both sides so an
  absent attribute equals its default, declarations and messages matched
  by name regardless of order, everything else in sequence. That is the
  equivalence `SchemaXmlAssert` has always defined for the oracles; it
  moves into the generator, and each difference is a problem naming the
  node it is on and both values. It subsumes every comparison `StatedRules`
  makes through the IR and reaches what the IR blurs: `nullValue`,
  `minValue`, `maxValue`, `valueRef`, a group's `semanticType`.
  `StatedRules` goes.
- **The IR comes from the resource,** which the check has proven equivalent
  to what the annotations would write; the flyweights and the join are the
  code-first ones over it. Nothing is written in this mode, as in 22.
- **Schemas are never merged,** the resource is found on `CLASS_PATH`, and
  XInclude resolves against its URI, all as 22 settled them.

## What gets built

- **The processor** runs discovery, mapping and validation in both modes.
  With a resource it reads it as today, hands the generator the rendered
  document beside the resource's text and URI, and writes no `schema.xml`.
- **The generator** resolves the resource's XIncludes, validates it against
  `sbe.xsd`, and compares it with the rendered document through
  `SchemaEquivalence`, the comparison moved out of `SchemaXmlAssert`, which
  becomes a wrapper over it so the tests and the compiler agree on one
  definition. A difference is a problem on the annotated node the path
  names, found by wire name, and on the package where no annotation
  corresponds:
  - a message, type or field the document has and the annotations lack,
    on the record or the package: `the schema has a message "Reject" (id 6)
    and no record maps it`, `the schema's NewOrder has a field "locateReqd"
    no component carries; add it, or declare it unmapped`, `the schema's
    ExecutionReport has a group "fills" no component carries`, `the schema
    has an enum "ExecType" and no declaration maps it`;
  - the reverse, on the annotation: `the schema has no message named
    "Order"`, `the schema's Order has no field named "nowhere"`;
  - an attribute or a value, on the annotation that states it: `the schema
    has presence="optional" on Order's price, not "required"`, `the schema
    has "F" for ExecType.TRADE, not "E"`.
  With no difference the resource is parsed, and the IR, the flyweights and
  the join follow as code-first.
- **What goes.** `StatedRules`; the partial-mapping members of the walk,
  the model, the writer and the templates (`UnmappedGroup`, `UnmappedData`,
  a composite no component carries, an uncarried field over a resource);
  the union over mapped messages only; the face taken from the document
  where the record names no type. `Member.Unmapped` stays for code-first's
  `unmapped` fields, which are in the document like any other.
- **The corpus.** `schemafirst` stays: `bigendian`'s records over the XML
  that package writes, expecting the generated sources to be the twin's.
  `partial` goes. `SchemaRoundTripTest` takes every schema of the corpus
  and the example round in both directions: the records compiled
  code-first write their schema, the same records compiled schema-first
  over it generate the same sources, byte for byte, and a package that is
  schema-first in the repository is compiled code-first with its
  `resource` spliced out and writes its resource back, equivalent.
- **The snippets.** Each difference above as a compile error on its node,
  against `venue.xml` beside the test; the partial-mapping snippets go.
- **The example.** `trading` is the showcase, and shipped schemas are
  frozen, so it goes schema-first: `trading.xml` moves to
  `src/main/resources/com/example/trading/`, the package names it, and the
  compiler's check replaces the oracle test for it. The pom's `SbeTool`
  execution reads it from there. `quotes` stays code-first, growing.
  `com.example.client` goes.
- **The documents.** `intent.md` adds increment 23, reworded from what 22
  claimed, and parks views; `type-mappings.md`'s schema-first section says
  the annotations are complete and the check is equivalence;
  `architecture.md`'s pipeline shows the resource entering beside the
  rendered document and the comparison; the guide's schema-first page is
  rewritten around the workflow, the errors and the build notes; the
  generator's, processor's, tests' and example's `AGENTS.md`.

## Criteria

- Every schema in the repository goes round in both directions with the
  same generated sources, byte for byte.
- A difference between the annotations and the resource, in either
  direction, is a compile error naming the node.
- `./mvnw verify` is green on a fresh clone, and the CI job passes on this
  pull request.

## Out of scope

Views: records over part of a schema someone else owns, decode-only, which
`intent.md` parks. Merging a resource with annotations. `unmapped` groups
and var-data, which complete mapping does not need. Recompiling on a
schema-only edit under Maven, which has no mechanism for it; the guide says
`clean`. Reading a schema from anywhere but the class path.
