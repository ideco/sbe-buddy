# sbe-buddy

Code-first SBE for Java: annotated records become an SBE XML schema, sbe-tool's
flyweights and a record codec, byte-identical to the hand-written schema.

```
docs/intent.md         what it is, scope, non-negotiables, increment list
docs/type-mappings.md  the target Java form of every sbe.xsd node; normative
docs/architecture.md   modules, the pipeline, the model, rules, Codec contract, testing, build
docs/next.md           the increment being built now
docs/notes.md          verified facts about sbe-tool, javac, Agrona and the build
docs/rpc.md            parked idea, out of scope

sbe-buddy-generator    the core: the schema model, its XML, the codec emitter, the corpus; no javac
sbe-buddy-api          what users compile against: annotations, Codec, TypeBinding, built-ins
sbe-buddy-processor    javac elements to the model; the only place javac appears
sbe-buddy-example      the running example, its trading.xml oracle, the end-to-end tests
reference/             sbe-tool's sources as a git submodule, for reading only
```

Build: `./mvnw verify` on JDK 21. The docs are listed in order of authority.
