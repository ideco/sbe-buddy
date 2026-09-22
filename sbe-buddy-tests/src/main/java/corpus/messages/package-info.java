/**
 * Two messages in one schema, with the descriptive attributes a message and its
 * fields carry and a block reserved wider than its fields need.
 */
@NullMarked
@SbeSchema(id = 1, version = 0, description = "What a message and its fields may say about themselves")
package corpus.messages;

import org.jspecify.annotations.NullMarked;

import net.concini.sbebuddy.SbeSchema;
