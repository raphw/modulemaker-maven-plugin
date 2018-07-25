This plugin allows the creation of a module-info.class for projects on Java 6 to Java 8 where a module-info.java file cannot be compiled.

Example use:

```xml
<plugin>
  <groupId>codes.rafael.modulemaker</groupId>
  <artifactId>modulemaker-maven-plugin</artifactId>
  <version>LATEST</version>
  <executions>
    <execution>
      <phase>process-classes</phase>
      <goals>
        <goal>make-module</goal>
      </goals>
    </execution>
  </executions>
  <configuration>
    <name>your.module</name>
    <packages>foo.bar</packages>
    <exports>foo.bar,qux.baz</exports>
    <requires>some.mod</requires>
  </configuration>
</plugin>
```

Note that all packages of the module must be named explicitly.

Under the Apache 2.0 license.
