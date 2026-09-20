# sbe-buddy

Code-first SBE for Java: annotated records become SBE IR, sbe-tool's flyweights
and a record codec, byte-identical to the equivalent XML schema.

```
docs/intent.md         what it is, scope, non-negotiables, increment list
docs/type-mappings.md  the target Java form of every sbe.xsd node; normative
docs/architecture.md   modules, generator/processor boundary, Codec contract, testing, build
docs/next.md           the increment being built now
docs/notes.md          verified facts about sbe-tool, javac and Agrona
docs/rpc.md            parked idea, out of scope

sbe-buddy-api          what users compile against: annotations, Codec, TypeBinding, built-ins
sbe-buddy-processor    generator/ (model to IR to sources, no javac) and processor/ (javac to model)
sbe-buddy-example      the running example, its trading.xml oracle, the end-to-end tests
```

Build: `./mvnw verify` on JDK 25. The docs are listed in order of authority.
