/**
 * A record that says its wire order at the top: its components declared out of
 * that order, and a deprecated field in the middle of the block that no
 * component carries. The document is the one a record in wire order would
 * write.
 */
@NullMarked
@SbeSchema(id = 1, version = 1)
package corpus.layout;

import org.jspecify.annotations.NullMarked;

import net.concini.sbebuddy.SbeSchema;
