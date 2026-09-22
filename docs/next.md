# Increment 15: The codec model

## Goal

The codec emitter decides and writes in one pass: it walks sbe-tool's IR,
finds each token's annotation by name, and writes Java strings as it
goes. Every construct since increment 6 added a branch to that pass, and
it now reads as a 1,400-line class whose methods take up to ten
parameters. This increment splits it in two. A walk builds a small model
of what each codec must do: per member, its shape on the wire, how it may
be absent and whether a binding stands in front of it. A writer renders
the model through the templates the emitter already has. The model is
the codec's grammar in one file, the way `Schema` is the XSD's and
`Annotated` the api's, so the shapes the codec can produce are readable at
a glance, and var-data in increment 16 becomes one more shape rather than
another branch.

## Settled before it started

- The generated code need not stay identical. No one depends on it, and
  the tests module proves every codec by behaviour: round trips at their
  edge values, refusals by message, lengths by the contract. Where the
  rewrite makes the output simpler, it takes the simpler output.
- Lean, not general. The model names the shapes this codec has and
  nothing more: no expression trees, no wrapper chains, no per-node
  render methods, no import computation, no template engine. Generated
  code keeps fully qualified names.
- The model is a third closed grammar beside `Schema` and `Annotated`,
  and follows their rules: one file, a nested record per node, sealed
  interfaces switched without `default`, records pure. `java-style.md`
  names it.
- Nothing tests the model's shape directly. A test that builds a model
  and compares it would be the twins the corpus just retired; the model
  is proven by the codecs it writes.
- `Mapping`'s face rules stay as they are; consolidating them is a
  separate pass.

## What gets built

- **`CodecModel.java`, the grammar.** Its own file in the generator,
  holding every node the codec can be made of:
  - `CodecModel(record, message, flyweights, header, baseline, bindings,
    body, helpers)`, one message's codec: the record and flyweight
    classes, the baseline it refuses below, the binding fields in first
    use, the message's body and the helper methods in first use.
  - `Body(wireOrder, constructorOrder)`: the members in the order the
    wire takes them, and the same members in the order the record's
    constructor takes its components.
  - `sealed interface Member`: `Field(component, property, shape,
    absence, binding)`, a component on a field or composite member;
    `Unmapped(property, shape)`, written as its null value and never
    read; `Group(component, property, path, entry, added)`, whose entry
    is a `Body`.
  - `sealed interface Shape`, what the flyweight call looks like:
    `Scalar`, `Text` (a char string through `ascii`), `Array(helper)`,
    `Enum(helper)`, `Set(helper)`, `Composite(helper)`, and
    `Constant(check)` for a constant, which carries no bytes.
  - `enum Absence`: `NONE` for a primitive that is always there,
    `REQUIRED` for a reference that must not be null, `OPTIONAL` for the
    null value, `ADDED` for a field or group appended above the baseline.
  - `sealed interface Helper`, one per thing mapped: `EnumPair`,
    `SetPair`, `ArrayPair`, `Ascii`, `CompositePair(body)`,
    `GroupMethods(body)`, each carrying what its template needs.
  Each record's javadoc names what it generates and stops.
- **`CodecWalk`, IR and annotations to the model.** One pass per message
  over sbe-tool's tokens with `GenerationUtil`, as today. For each token
  it finds the annotated component once, by wire name, and settles the
  member's shape, absence and binding. Helpers are registered in a
  `LinkedHashMap` keyed by what they map, the declaration or the path,
  so the first use orders them and a nested helper registers before the
  one that uses it. A construct the codec does not cover yet is a
  `Problem` collected on the message, and the walk stops for that
  message; the thrown `Rejected` goes. The per-message state, the
  flyweights package, the helpers and the bindings, is the walk's own,
  so no method takes it as a parameter.
- **`CodecWriter`, the model to source.** A switch over the model's
  types, one template fill per case: absence wraps a field's write and
  read in one place, a binding wraps them in another, and
  `encodedLength` is a walk over the bodies, the header and block, then
  per group its header and each entry's own block and groups.
- **`CodecTemplates`, the templates.** The emitter's sixty-odd `Template`
  constants move to a file of their own, grouped as today by shape.
- **`CodecEmitter`** keeps `emit`, the entry `Generator` calls, and
  shrinks to it: the walk and the writer per message, the problems
  gathered, and the all-or-nothing hand-off to the output.
- **The documents.** `architecture.md`'s generation section describes
  the walk, the model and the writer; `java-style.md` names the codec
  model as the third grammar; `intent.md` ticks 15.

## Criteria

- Every test of the tests module, the example and the processor passes
  unchanged: the round trips, the refusals, the lengths, the snippets.
- `GeneratorTest`'s problems for the constructs the codec lacks are the
  same `Problem`s on the same messages.
- `CodecModel.java` reads as the list of what a codec can contain; the
  writer's switch reads as what each of those looks like in Java; no
  method of the walk, the writer or the emitter takes more than five
  parameters.
- `./mvnw verify` is green on a fresh clone, and the CI job passes on this
  pull request.

## Out of scope

Var-data, increment 16, which this makes one shape, one helper and one
length term. `Mapping`'s face rules. Imports in generated code. Any
change to what a codec does, as opposed to how its source reads.
