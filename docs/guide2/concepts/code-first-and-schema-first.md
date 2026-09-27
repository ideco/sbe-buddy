# Code-first and schema-first

An SBE schema may begin in your Java application, or it may already exist
as XML shared with other applications. sbe-buddy supports both starting
points. In each case, you write annotated Java declarations and get standard
SBE flyweights and record codecs. The choice determines where the XML comes
from and how the build checks your declarations.

## When Java defines the schema

In code-first mode, the annotated declarations define the schema.
sbe-buddy generates its XML and uses that XML to generate the flyweights
and codecs. The XML is included in the application's build output, so other
tools and languages can use the same schema.

This is useful when you own the schema and want to develop it alongside
your Java types. Adding a field to an annotated record adds that field to
the generated schema. The annotations make its ID, type and version part
of the change you review.

The [first-message tutorial](../tutorials/getting-started.md) uses this mode.

## When XML defines the schema

In schema-first mode, `@SbeSchema.resource` identifies an existing XML
resource. Your annotations must describe that schema completely. The build
checks the two representations and generates the flyweights and codecs
from the resource only when they agree.

This is useful when the XML is already an agreed interface between
applications, or when you want to keep an explicit schema file under
review. A change to a Java declaration that would alter the schema then
causes a compilation error until the declarations and XML agree again.

You still write the Java declarations yourself. Schema-first does not
generate records from XML, and it does not support records that map only a
selection of the schema. A schema with ten messages needs declarations for
all ten, even if an application uses only one of them.

## Matching covers more than the bytes

Two schema descriptions can use the same wire layout while disagreeing
about names, descriptions or other metadata. Schema-first checks those
details as well. For example, changing a field's description leaves the
encoded message unchanged, but the description in Java must still match
the one in the XML.

The comparison allows differences that do not change the schema's
declarations. An omitted attribute matches its explicit XSD default.
Top-level types and messages can appear in a different order. XML
formatting does not matter.

Order within declarations does matter: message fields, composite members,
group members, enum values and set choices must match the XML's order.
When record component order needs to differ from wire order, a `layout`
declaration can state the wire order explicitly.

A successful comparison confirms that the Java declarations and the
selected XML describe the same schema. It does not establish compatibility
with an earlier release of that schema. Changing both representations to
agree is still a schema change whose effect on existing readers needs to
be considered. [Schema versions and absence](schema-versions-and-absence.md)
explains the compatibility rules.

## Moving between the two modes

A project can begin code-first and later keep its generated XML as a
resource. With `resource` set, that document becomes the input for
generation, and the build checks the declarations against it. This gives
you a way to preserve an agreed schema while continuing to work on the
Java application.

The complete Java declarations also make the reverse move possible.
Removing `resource` returns to generating XML from the annotations. The
result describes the same schema, although formatting, comments and the
order of top-level declarations need not reproduce the original document.

Switching modes does not change the schema's evolution rules. If the XML
is shared with other applications, agreeing a new schema version remains
a separate decision from choosing which file supplies it to the build.

## Application types remain a separate choice

Both modes support [bindings](application-types-and-wire-representations.md).
A message can use `BigDecimal` for a price while the XML declares a
mantissa-and-exponent composite. The annotations still identify the
composite, and the binding supplies the Java conversion without changing
the schema.

For the practical steps, follow
[Map an existing XML schema](../how-to/map-an-existing-schema.md).
