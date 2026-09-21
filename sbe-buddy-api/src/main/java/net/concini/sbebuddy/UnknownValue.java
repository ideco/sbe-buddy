package net.concini.sbebuddy;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * On one constant of an {@code @SbeEnum}, in place of {@code @SbeEnumValue}:
 * every wire value the enum does not know decodes to it, so a reader keeps
 * working when a writer adds values. It has no wire form, so encoding it is an
 * {@code IllegalArgumentException}. The Java side; contributes no
 * {@code validValue} to the schema.
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.FIELD)
public @interface UnknownValue {
}
