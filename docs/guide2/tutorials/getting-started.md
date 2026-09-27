# Encode and decode your first message

In this tutorial, you will create a small Java project, declare an SBE
message as a record, and encode and decode it. You will also inspect the
generated schema to see how the record maps to SBE.

You need JDK 21 or newer and Maven 3.9 or newer installed. Check that Maven
is using the expected JDK:

```sh
mvn --version
```

The output should report Java version 21 or higher. Maven will download the
dependencies when you first build the project.

## 1. Create the project

Create a directory for the project and its Java package:

```sh
mkdir sbe-buddy-tutorial
cd sbe-buddy-tutorial
mkdir -p src/main/java/com/example/trading
```

Run the remaining Maven commands from this directory. Create `pom.xml`
with the following contents:

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.example</groupId>
    <artifactId>sbe-buddy-tutorial</artifactId>
    <version>1.0-SNAPSHOT</version>

    <properties>
        <maven.compiler.release>21</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <sbe-buddy.version>0.2.0</sbe-buddy.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>net.concini</groupId>
            <artifactId>sbe-buddy-api</artifactId>
            <version>${sbe-buddy.version}</version>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.16.0</version>
                <configuration>
                    <annotationProcessorPaths>
                        <path>
                            <groupId>net.concini</groupId>
                            <artifactId>sbe-buddy-processor</artifactId>
                            <version>${sbe-buddy.version}</version>
                        </path>
                    </annotationProcessorPaths>
                </configuration>
            </plugin>
            <plugin>
                <groupId>org.codehaus.mojo</groupId>
                <artifactId>exec-maven-plugin</artifactId>
                <version>3.6.4</version>
                <configuration>
                    <executable>${java.home}/bin/java</executable>
                    <arguments>
                        <argument>--add-opens=java.base/jdk.internal.misc=ALL-UNNAMED</argument>
                        <argument>-classpath</argument>
                        <classpath/>
                        <argument>com.example.trading.Example</argument>
                    </arguments>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

The API dependency supplies the annotations and codec interfaces. The
annotation processor generates code when Maven compiles your records.
Agrona, which supplies the buffers used by the codecs, is included through
the API dependency.

The Exec plugin will run the example. Its `--add-opens` argument enables
Agrona's buffer access at runtime; compilation does not need that argument.

## 2. Declare the schema and message

Create `src/main/java/com/example/trading/package-info.java`:

```java
@SbeSchema(id = 100, version = 0)
package com.example.trading;

import net.concini.sbebuddy.SbeSchema;
```

This package defines a schema with ID 100 at version 0. Messages declared
in the package belong to that schema.

Create `src/main/java/com/example/trading/PlaceOrder.java`:

```java
package com.example.trading;

import net.concini.sbebuddy.SbeField;
import net.concini.sbebuddy.SbeMessage;

@SbeMessage(id = 1)
public record PlaceOrder(
        @SbeField(id = 1) long orderId,
        @SbeField(id = 2) int quantity) {}
```

`@SbeMessage` assigns template ID 1 to the message. Each `@SbeField`
assigns an ID to a field within it. Here, `long` selects SBE `int64` and
`int` selects `int32`. The fields appear in record component order.

## 3. Compile and inspect the schema

Compile the project:

```sh
mvn compile
```

The build should finish with `BUILD SUCCESS`. Open the generated schema at
`target/classes/com/example/trading/schema.xml`. Its message declaration
contains:

```xml
<sbe:message name="PlaceOrder" id="1">
    <field name="orderId" id="1" type="int64"/>
    <field name="quantity" id="2" type="int32"/>
</sbe:message>
```

Notice that the message and field IDs match the Java annotations, and the
field types match the record's `long` and `int` components. This is an SBE
schema that other SBE tools can use.

Under `target/generated-sources/annotations`, you will also find:

```text
com/example/trading/PlaceOrderCodec.java
com/example/trading/sbe/PlaceOrderEncoder.java
com/example/trading/sbe/PlaceOrderDecoder.java
```

The codec works with your `PlaceOrder` record. The encoder and decoder are
the standard flyweights generated by sbe-tool. We will use the codec for
the round trip.

## 4. Encode and decode an order

Create `src/main/java/com/example/trading/Example.java`:

```java
package com.example.trading;

import org.agrona.concurrent.UnsafeBuffer;

public final class Example {
    public static void main(String[] args) {
        var order = new PlaceOrder(42L, 100);
        var codec = new PlaceOrderCodec();
        var buffer = new UnsafeBuffer(new byte[codec.encodedLength(order)]);

        int bytesWritten = codec.encode(order, buffer, 0);
        PlaceOrder decoded = codec.decode(buffer, 0);

        System.out.println("Bytes written: " + bytesWritten);
        System.out.println("Decoded: " + decoded);
        System.out.println("Equal: " + order.equals(decoded));
    }
}
```

`encodedLength` gives us the buffer size needed for this order, including
the message header. Both `encode` and `decode` take a byte offset pointing
to the start of that header; here, we use offset 0.

Compile the new class and run it:

```sh
mvn compile exec:exec
```

Alongside Maven's build messages, you should see:

```text
Bytes written: 20
Decoded: PlaceOrder[orderId=42, quantity=100]
Equal: true
```

The 20 bytes consist of the standard eight-byte SBE message header, the
eight-byte order ID, and the four-byte quantity. The codec writes the header
and fields, then reads them back into a new record. `Equal: true` confirms
that the decoded record has the same component values as the original.

Change the quantity in `new PlaceOrder(42L, 100)` to `250` and run the same
command again. The decoded quantity should change to `250`; the length
stays at 20 bytes because both fields have fixed widths.

A codec reuses mutable flyweights. You can reuse the instance for more
orders on the same thread, but do not share it between threads.

## Where to go next

You now have a project that generates an SBE schema and codecs from a Java
record, and an application that uses the codec to encode and decode it.

For how fields, repeating groups and variable-length data fit into an
encoded message, read
[How an SBE message is laid out](../concepts/message-layout.md).

For buffer sizing, codec reuse and dispatching several message types, follow
[Encode and read multiple messages](../how-to/use-codecs-in-an-application.md).

To work with a richer application type, follow
[Use BigDecimal for a price](../how-to/use-bigdecimal-for-a-price.md). The
project you just created has the build configuration that guide needs.
For the choices behind that conversion, read
[Application types and wire representations](../concepts/application-types-and-wire-representations.md).

To keep an XML schema as the input to your build, follow
[Map an existing XML schema](../how-to/map-an-existing-schema.md).
