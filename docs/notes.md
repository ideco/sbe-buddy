# Notes

Verified facts about sbe-tool, javac, Agrona and the build that the
implementation relies on, especially the surprising ones. Each entry says how it was
verified. Nothing else goes here: no design discussion, no to-dos, no
alternatives considered.

## sbe-tool 1.40.2

- `JavaGenerator` calls `setPackageName` on its output manager only for the
  types-package override. The caller must call
  `setPackageName(ir.applicableNamespace())` before `generate()`, or every
  file is named `null.<Class>` although its `package` line is right.
  (Spike experiment, 2026-09; re-check when increment 3 relies on it.)
- `JavaGenerator.generate()` opens `MessageHeaderEncoder` and
  `MessageHeaderDecoder` twice, once from `Ir.types()` and once as the
  header stub, with identical content. An output manager over `Filer` must
  treat the second open as a no-op, because `Filer` cannot recreate a file.
  (Spike experiment, 2026-09.)
- `XmlSchemaParser` with `stopOnError`, `warningsFatal` and
  `suppressOutput` throws `IllegalArgumentException` from a schema warning
  and prints nothing. (Spike experiment, 2026-09.)
- `XmlSchemaParser.parse` never validates against `sbe.xsd`; that is a
  separate `validate(xsdFilename, InputSource, options)`, which wants the
  XSD as a file path and sets no error handler, so the JDK's default prints
  each violation to stderr and carries on. Validate with
  `javax.xml.validation` and `fpl/sbe.xsd` from the jar instead.
  (`XmlSchemaParser.java` and `SbeTool.java`, read 2026-09-20.)
- Every parser error goes through `ErrorHandler.error(String)`, a public
  method of a public non-final class with a public `(PrintStream,
  ParserOptions)` constructor, and `ParserOptions` carries an
  `errorPrintStream`. The node never reaches the handler: `handleError`
  prefixes the message with `at <parent name="P"> <node name="N"> ` from a
  private `formatLocationInfo`. 57 `handleError` sites, about 40 distinct
  rules, in `Message`, `Field`, `CompositeType`, `EncodedDataType`,
  `EnumType` and `XmlSchemaParser`; `MessageSchema.validate` adds
  `sinceVersion` above the schema version. (Read 2026-09-20.)
- `sbe.xsd` declares `messageSchema` and `message` at top level, so both
  are namespace-qualified (`<sbe:message>`) and every other element is
  local and unqualified. The parser matches by local name and accepts an
  unqualified `message` that the XSD rejects, so validating against the
  XSD is a separate check. (Spike experiment, 2026-09-20.)
- `MessageSchema`'s constructors are package-private and `Message`,
  `CompositeType`, `EnumType` and `SetType` are built from DOM nodes, so
  `parse(InputSource)` is the only way to obtain a `MessageSchema`.
  (Read 2026-09-20.)
- `uk.co.real_logic.sbe.ir.GenerationUtil` (`collectFields`,
  `collectGroups`, `collectVarData`) and
  `uk.co.real_logic.sbe.generation.java.JavaUtil` (`formatPropertyName`,
  `formatClassName`, `formatGetterName`) are public; `JavaGenerator`
  derives every flyweight member name through them. (Read 2026-09-20.)
- `IrEncoder` and `IrDecoder` read and write the `.sbeir` file `SbeTool`
  accepts in place of XML and emits under `-Dsbe.generate.ir=true`.
  `IrEncoder` imports `org.agrona.concurrent.UnsafeBuffer`, so writing an
  IR file inside javac needs the Agrona JVM flag. `IrGenerator`, `Ir`,
  `Token` and `Encoding` import only `org.agrona.Verify`; `JavaGenerator`
  refers to the `DirectBuffer` and `MutableDirectBuffer` interfaces by
  class literal, and whether loading those touches `UnsafeApi` is
  unverified. (Read 2026-09-20.)
- The decoder's `wrap(buffer, offset, actingBlockLength, actingVersion)`
  sets `limit = offset + actingBlockLength`, so an older decoder skips
  fields a newer version appended to the block. (`JavaGenerator.java`,
  message `wrap`, read 2026-09-20.)
- Below the acting version, a guarded getter returns the encoding's
  applicable null value for a primitive, `NULL_VAL` for an enum, count zero
  for a group, and wraps var-data with length zero. Encoders have no
  version guard. (`JavaGenerator.java`, `generateFieldNotPresentCondition`
  and the group and var-data guards, read 2026-09-20.)
- The parser checks `sinceVersion <= messageSchema.version` on messages,
  types and valid values (`MessageSchema.java`) but does not check that
  later-version fields follow earlier ones; offsets are computed in
  declaration order (`Message.computeAndValidateOffsets`). (Read
  2026-09-20.)
- `semanticVersion` is a free string emitted as the `SEMANTIC_VERSION`
  constant; `deprecated` marks the generated member `@Deprecated`.
  (`JavaGenerator.java`, read 2026-09-20.)
- A composite named as a group's `dimensionType` must hold `blockLength` and
  `numInGroup`, and a composite named as a `data` field's type must hold
  `length` and `varData`. Each is warned unless its primitive is what SBE
  expects, `uint8` or `uint16` for the dimensions and `uint8`, `uint16` or
  `uint32` for the length, and a `uint32` length errors unless it carries a
  `maxValue` no greater than `Integer.MAX_VALUE`. The message header needs
  `blockLength`, `templateId`, `schemaId` and `version`, all warned unless
  `uint16`, and ignores any further member.
  (`CompositeType.java`, read 2026-09-20, and the corpus.)
- A `ref` resolves against `/messageSchema/types/*[@name=...]`, so its target
  must be declared at the top level; a ref to a member of another composite
  does not resolve. `ref` carries only `name`, `type`, `offset`,
  `sinceVersion` and `deprecated`, and the parser reads the last two onto the
  copied type. (`CompositeType.java`, read 2026-09-20, and the corpus.)
- `nullValue` on a type whose presence is not `optional` is a warning, which
  `warningsFatal` makes fatal; so is an enum valid value equal to the
  encoding's null value. (`EncodedDataType.java` and `EnumType.java`, read
  2026-09-20, and the corpus.)
- `sbe.xsd` types every `name` attribute as `symbolicName_t`, whose pattern
  is `([A-Z]|[a-z]|_)([0-9]|[A-Z]|[a-z]|_)*`, and a `valueRef` as
  `qualifiedName_t`, two of those joined by a dot. A `field`'s and a
  `message`'s `id` is `xs:unsignedShort`, so 0 to 65535, and `offset` is
  `xs:unsignedInt` with no default, which is why an absent one is not the
  same as `offset="0"`. (`fpl/sbe.xsd` in the sbe-tool jar, read
  2026-09-21.)
- The XSD gives `data` the same attribute groups as `field`, so `presence`,
  `valueRef`, `epoch` and `timeUnit` are declared for it. `parseDataField`
  reads presence, epoch and timeUnit, and `Field.validate` checks a
  `valueRef` resolves to an enum valid value, but nothing downstream uses
  them for a data field. (`Message.java` and `Field.java`, read 2026-09-20,
  and the corpus.)

## javac

- No annotation processor runs after an error: a negative compile test sees
  only the diagnostics of the round that failed. (Spike, 2026-09.)
- `Filer.createSourceFile` throws `FilerException` for a name already
  created in the same compilation. (Spike, 2026-09.)
- `Messager` cannot position an annotation *value* on a record component;
  the diagnostic lands on the annotation. (Spike, 2026-09.)
- A `Class`-typed member read through an annotation instance from
  `Element.getAnnotation` throws `MirroredTypeException` carrying the
  `TypeMirror`; the same member read from the `AnnotationMirror`'s element
  values is a `ClassType`. Discovery reads every `Class` member from the
  mirror. (Spike experiment, javac 21, 2026-09-20.)
- A type loaded from a class file, from a jar or a classes directory,
  exposes its `CLASS`-retained annotations through `getAnnotationMirrors()`
  on the type and on each `RecordComponentElement`, with the components in
  declaration order; `MessageHeader` from the api jar reads as
  `@SbeComposite(name="messageHeader")` over four `@SbeType(primitiveType=
  UINT16)` components. (Spike experiment, javac 21, 2026-09-21.)
- `AnnotationMirror.getElementValues()` holds only the members written
  explicitly; `Elements.getElementValuesWithDefaults` fills the rest.
  Discovery reads every member through the latter, so a member left at its
  default is the default's value. (`javax.lang.model` Javadoc, and the
  spike above.)
- `PackageElement.getEnclosedElements()` returns the package's types in the
  order javac entered them: compilation units in the order given, and
  declaration order within a unit; `TypeElement.getEnclosedElements()`
  returns record components in declaration order, and
  `ElementFilter.recordComponentsIn` keeps it. (Spike experiment, javac
  21, 2026-09-21.)
- `RoundEnvironment.getElementsAnnotatedWith` returns only elements of the
  types being compiled; a type read from a jar is never in a round, however
  many of our annotations it carries. So the packages a round offers are
  the compilation's own, and the api's package never becomes a unit of
  work although `MessageHeader` carries `@SbeComposite`. (Every corpus
  case, which resolves `MessageHeader` from the api jar and compiles
  without a diagnostic.)
- `Messager.printMessage(kind, message, element, mirror)` positions the
  diagnostic on the annotation rather than on the element's own
  declaration. (`PlacementTest`, on a message whose `@SbeMessage` is
  written on the line above the record.)
- `Filer.createResource(CLASS_OUTPUT, "a.b", "schema.xml", origin)` writes
  `a/b/schema.xml` under the class output directory, so the resource ships
  in the jar beside the package's classes and is on the test classpath of
  the module that declared it. (`javax.annotation.processing` Javadoc, and
  the example's `SchemaResourceTest`.)
- From JDK 23 javac performs no annotation processing unless `-processor`,
  `--processor-path` or `--processor-module-path` is set, or `-proc` is
  `only` or `full`; discovery from the compile classpath is gone. Reaching
  the processor through `annotationProcessorPaths`, as the example does,
  sets `--processor-path` and is unaffected.
  (`maven-compiler-plugin:4.0.0-beta-5` plugin descriptor, `proc` and
  `annotationProcessorPaths`, read 2026-09-20.)

## Error Prone 2.50.0

- On JDK 21 Error Prone refuses to run without
  `-XDaddTypeAnnotationsToSymbol=true` and fails the compilation with that
  sentence as the whole message. (Spike experiment, 2026-09-20.)

## Agrona 2.6.1

- `UnsafeApi` in the sources jar is a stub; the shipped bytecode calls
  `jdk.internal.misc.Unsafe` directly, so any JVM that loads an Agrona
  buffer class needs `--add-opens java.base/jdk.internal.misc=ALL-UNNAMED`.
  javac needs it only if the processor touches a buffer. (Sources jar,
  read 2026-09-20.)

## Maven 4.0.0-rc-6

- `.mvn/jvm.config` is parsed by `bin/JvmConfigParser.java`, which strips
  everything from a `#` to the end of the line and expands
  `${MAVEN_PROJECTBASEDIR}`. The `only-script` wrapper execs
  `$MAVEN_HOME/bin/mvn`, so `./mvnw` inherits that; the classic wrapper
  joins the file with `tr` and passes the comment to the JVM, which fails
  with `Could not find or load main class #`. (`bin/mvn`,
  `bin/JvmConfigParser.java` and both `mvnw` variants, read 2026-09-20.)
- `bin/mvn` captures that parser's stderr into `MAVEN_OPTS` and `eval`s it,
  so a JVM that prints a banner, as any JDK does when `JAVA_TOOL_OPTIONS` is
  set, breaks the launcher before Maven starts. CI and a plain workstation
  are unaffected; a container that sets it must pass those flags in
  `MAVEN_OPTS` instead. (Spike experiment, 2026-09-20.)

## Surefire 3.6.0

- The plugin's own descriptor names junit-platform-launcher 1.14.4, but it
  resolves the launcher to the JUnit Platform version it finds on the test
  classpath, 6.1.3 here, and runs that. Declaring the launcher through the
  JUnit BOM takes the alignment out of surefire's hands and puts the version
  in the root POM. (Spike experiment with `-X`, 2026-09-20.)

## Spotless 3.10.2

- `removeUnusedImports` and `importOrder` work on this code. The CleanThat
  step does not: with any mutator configured it throws
  `InvocationTargetException` on `Corpus.java`, which `spotless:apply`
  reports as a lint and otherwise ignores under `sourceJdk` 21, so the
  step silently changes nothing. (Spike experiment, 2026-09-20.)
