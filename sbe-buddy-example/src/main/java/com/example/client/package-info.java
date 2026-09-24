/**
 * A client of the trading venue, mapping the part of the venue's schema it
 * needs. The schema is the venue's own {@code trading.xml}, read from the class
 * path rather than written; the records name the fields they carry, and the
 * rest of each message is written empty and passed over. The venue's declared
 * types and bindings are reused across the package boundary.
 */
@NullMarked
@SbeSchema(id = 91, version = 0, headerType = SessionHeader.class, resource = "/trading.xml")
package com.example.client;

import org.jspecify.annotations.NullMarked;

import net.concini.sbebuddy.SbeSchema;

import com.example.trading.SessionHeader;
