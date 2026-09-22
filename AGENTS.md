# sbe-buddy

Code-first SBE for Java: annotated records become an SBE XML schema, sbe-tool's
flyweights and a record codec, byte-identical to the hand-written schema.

```
docs/intent.md         what it is, scope, non-negotiables, increment list
docs/type-mappings.md  the target Java form of every sbe.xsd node; normative
docs/architecture.md   modules, the pipeline, the model, rules, Codec contract, testing, build
docs/next.md           the increment being built now
docs/notes.md          verified facts about sbe-tool, javac, Agrona and the build
docs/guide/            the user guide: getting started, one page per construct, how-to, concepts
docs/rpc.md            parked idea, out of scope

sbe-buddy-generator    the core: the schema model, the annotations as data, the mapping between them, the XML, the codec emitter, the corpus; no javac
sbe-buddy-api          what users compile against: annotations, Codec, TypeBinding, built-ins
sbe-buddy-processor    javac elements to the annotations as data; the only place javac appears
sbe-buddy-example      a realistic annotated schema with its oracle; the integration proof
sbe-buddy-tests        the corpus compiled by the real build: a schema package, its oracle and its round trips per case, run against the generated code
reference/             sbe-tool's sources as a git submodule, for reading only
```

Build: `./mvnw verify` on JDK 21. The docs are listed in order of authority,
and a change that alters behaviour updates the ones describing it in the same
commit.
