Overview
===
An example of using maven-vbox-plugin.

This app will:

- Create a "Hello World" war.
- Create a basic VM and install Tomcat6 on it.
- Package the war in an RPM, with a dependency on Tomcat6.
- Deploy that RPM to the VM.
- Run an integration test.

References
---
- http://maven.apache.org/guides/mini/guide-webapp.html