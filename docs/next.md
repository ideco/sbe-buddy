# Increment 26: closing

## What was built

A typed reader for every message of the schema, `<Message>Reader` beside
its codec, over sbe-tool's decoder: the message as a flat sequence of
stages in wire order, `skip()` on every stage, a stage answering while the
reader is inside it, and `bound()` on each stage where a record maps the
message. Steps 0 to 2 of the plan, and the reader half of step 4, as
`flyweights.md` has them.

## What was cut

The writer (step 3 and the writer half of step 4), the codec rewritten
over the bound stages (step 5) and the union readers (step 6). The reasons
are under Superseded and Open in `flyweights.md`: the write side's
guarantees cost more than they gave, in speed and in surface, and a
binding cannot reach a generated encoder. Writing stays sbe-tool's
encoders' in place, and the codec's from a record.

## The closing step

One pull request from `main`:

- The generator loses the writer: its walk, model, writer, templates and
  sub-chains, and the emitter writes readers alone. `FaceWriter` keeps the
  null writers, which the codec uses for `unmapped` fields.
- The tests lose the writer's: `HandWriterTest`, `WriterAssert`, the
  corpus tests' writer cases, the writer halves of `BoundStagesTest` and
  `HandBoundTest`, the writer goldens, the writer's name-clash snippets.
- The documents say reader where they said reader and writer: the guide's
  flyweights page, `type-mappings.md`, `architecture.md`, the glossary,
  the README, `intent.md`, the module `AGENTS.md` files, and
  `flyweights.md` itself.
- `TradingBenchmark` stays, over the codecs as they are.

**Done when** `./mvnw -Prelease clean install` is green with every
existing reader test unchanged, and no source or document names a writer
of ours.

## After it

`intent.md` ticks 26 as built. Increment 27, the API pass, is the next to
plan, in a `next.md` of its own.
