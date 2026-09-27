# Writing the guide

This directory is the replacement user guide, being developed alongside
`docs/guide`. It will be published as a separate website. These instructions
are for authors and must not appear in the published navigation.

## Audience and scope

Write for Java developers who have worked with SBE. Assume Java competence
and some familiarity with schemas, messages and flyweights; do not assume
detailed knowledge of SBE's encoding or evolution rules. Explain the SBE
concepts needed for the subject at hand, with links for further reading.

The guide must stand on its own. Readers should not need the repository,
its test corpus, its increment history or internal design documents.
`docs/type-mappings.md`, `docs/architecture.md` and similar documents are
sources for authors, not destinations for guide readers. The repository's
document authority order still applies when checking behavior.

## Give each page one purpose

Follow [Diátaxis](https://diataxis.fr/):

- **Tutorials** teach through a complete, guided experience. Choose a path,
  provide prerequisites and runnable examples, show expected results, and
  end at a clear achievement. Move alternatives and extended explanation
  elsewhere.
- **How-to guides** help an already competent reader accomplish a specific
  task. State the starting conditions, give the necessary actions, and show
  how to recognize success. Keep the scope tied to the task.
- **Reference** supports lookup. Describe mappings, supported forms and
  constraints consistently. Small Java/XML examples belong here when they
  make a mapping clear.
- **Concepts** explain relationships, reasons and tradeoffs. Use a reader's
  question to bound the subject; link to procedures instead of embedding
  them.

Separate content by reader need, not simply by annotation or feature. A
feature may need more than one kind of page. Do not split every paragraph
into its own page: brief context and relevant qualifications can stay where
the reader needs them. Improve a useful slice at a time rather than filling
out an empty four-part structure.

## Responsibilities of the documentation

- The project README introduces sbe-buddy, shows a representative example,
  and directs readers to documentation.
- The guide teaches its use, explains the model, and provides construct
  reference showing how declarations fit together.
- API Javadoc documents individual annotation members, defaults, method
  contracts, exceptions and lifecycle. Link to it for exhaustive API detail
  rather than reproducing member inventories in the guide. Restate a rule
  when it is necessary to complete a task correctly.
- `docs/glossary.md` supplies the shared public vocabulary. Use and link to
  it; do not create a competing glossary here.

Use relative links between guide pages. The existing glossary is a public
companion even though it currently lives outside this directory. Website
publication must include it and resolve those links. Until the published
API documentation destination is established, identify the relevant Java
types by name; do not invent URLs or send readers to source files instead.
Navigation should list available pages, not unwritten placeholders. Keep
site-framework details out of the prose.

## Voice and selection

Write directly to the reader in plain, natural English. Start with their
situation or question, then explain what they need to know. Use concrete
subjects and short, connected sentences. A paragraph should develop one
idea; break up sentences that accumulate rules, exceptions and examples.

Use the glossary's vocabulary: application type, record component, wire
representation, binding. Avoid internal metaphors such as "face" and vague
phrases such as "another Java shape". Distinguish fields, groups and
variable-length data; use "member" when referring to them collectively.

Do not narrate how the implementation was built. Omit increment history,
test-corpus tours, incidental generator mechanics and speculative promises
such as "planned" or "not built yet". Describe current capabilities and
limitations when they affect the reader's task.

Keep details that change a user's decision or prevent a plausible mistake:
absence, information loss, schema compatibility, precision, ownership and
thread safety where relevant. Put obscure restrictions and diagnostic
details in reference or troubleshooting when they are useful there. Do not
turn every known implementation fact into guide content.

## Examples and verification

Use small, locally understandable examples. Do not require readers to know
the history of the example application's schema. Keep names and values
consistent across related pages. State which parts of a schema are choices
made for an example, particularly units, scales and null representations.

For complete files, show their filenames, packages and imports. Label
fragments and their required context clearly. Keep a how-to's setup brief
when a configured project is an explicit prerequisite.

Check technical claims against current Javadoc, normative documents and
implementation evidence. Existing guide prose is material to assess, not an
authority to copy. Resolve contradictions before carrying them forward.

Compile substantial Java examples with the real annotation processor and
check the demonstrated behavior, including the generated XML where shown.
Verify relative links and expected output. Review the prose separately:
could the intended reader follow it without knowing how the code was built?
