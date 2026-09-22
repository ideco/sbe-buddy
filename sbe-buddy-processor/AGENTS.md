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

## Tests

- `Javac` compiles in memory with `-proc:only`, so generated code is not
  compiled here. sbe-buddy-tests is where it meets javac.
- `AnnotationMistakesTest` holds one test per rule, written as the source a
  user types around a shared package, imports and enclosing record, with
  `codecs = false`. It asserts every diagnostic, the text of the line it lands
  on, and that nothing was written. Where a rule allows something, a test
  asserts it compiles clean.
- `PlacementTest` proves placement and all-or-nothing once per rule layer.
- `IncrementalCompilationTest` compiles real directories, whole and then one
  record alone, and expects the same output.
