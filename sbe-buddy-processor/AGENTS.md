# sbe-buddy-processor

The javac front-end and nothing more: elements to `Annotated`, problems to
`Messager`, sources to `Filer`. Everything else is the generator's.

```
SbeProcessor         rounds, the package as unit of work, placing problems
Discovery            javac elements to Annotated, and the rules only javac can see
FilerOutputManager   Agrona's DynamicPackageOutputManager over Filer
```

## Rules of the front-end

- The unit of work is the package of any annotated element, so recompiling
  one record without its `package-info.java` still regenerates the package.
- Each package is generated once, in the first round that shows it, never in
  the `processingOver` round: `Filer` cannot recreate a file.
- A package with an error writes nothing, whichever step found it. One with
  warnings only is written whole.
- A problem lands on the element it names, with the `AnnotationMirror` where
  there is one, through the identity maps `Discovery` returns.
- Discovery orders the top level itself, messages by id and declarations by
  qualified name, never in javac's order.
- `Class`-typed members are read from the `AnnotationMirror`, never through
  an annotation instance (`notes.md`).
- Never load an Agrona buffer class: a user's javac would need a JVM flag.
- A resource is read through `Filer.getResource` on `CLASS_PATH` alone, the
  one location Maven, Gradle and a jar agree on (`notes.md`), and its URI
  goes to the generator for XInclude, beside the schema the mapping made,
  which the generator holds against it. Schema-first writes no
  `schema.xml`: Gradle's `jar` fails on the duplicate.

## Tests

- `Javac` compiles in memory with `-proc:only`, so generated code is not
  compiled here. sbe-buddy-tests is where it meets javac. A test that needs
  what javac reports only when it compiles, a deprecated member's use, passes
  options of its own.
- `AnnotationMistakesTest` holds one test per rule, written as the source a
  user types around a shared package, imports and enclosing record, with
  `codecs = false`, which the face rules ignore, except the unions, which
  need codecs. A snippet breaks rules of one layer only: a mapping error
  stops the pipeline before the join, so a face rule beside it would go
  unreported. It asserts every diagnostic, the text of the line it lands
  on, and that nothing was written. Where a rule allows something, a test
  asserts it compiles clean. The schema-first snippets read `venue.xml`
  from the test resources, which the test class path carries.
- `PlacementTest` proves placement and all-or-nothing once per rule layer.
- `IncrementalCompilationTest` compiles real directories, whole and then one
  record alone, and expects the same output.
- `SchemaRoundTripTest` compiles every schema package of the corpus and the
  example code-first, a package that is schema-first in the repository
  with its `resource` spliced out, then the same records schema-first over
  the schemas the first run wrote, and expects the same generated sources
  and, for the spliced packages, their resource written back equivalent:
  the proof that the switch loses nothing in either direction, over every
  construct the repository has. A partial package reads its resource in
  both runs, since its records cannot write it.
