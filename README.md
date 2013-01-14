Overview
===
An example of using maven-vbox-plugin.

This app will:

- Create a "Hello World" war.
- Create a basic VM and install Tomcat6 on it.
- Package the war in an RPM, with a dependency on Tomcat6.
- Deploy that RPM to the VM.
- Run an integration test.

Pre-requisites
---

- Maven
- Git
- SVN
- VirtualBox
- rpmbuild (for the RPM plugin)

Firstly you need two get and compile two items. My plugin that creates VirtualBoxes:

	git clone https://github.com/alexec/maven-vbox-plugin.git
	cd maven-vbox-plugin
	mvn install

And the RPM plugin which we'll use to create the jar. There's a small bug in it which means if you're not creating an RPM on your target OS, yum might refuse to install it. We need to make a small patch, [MRPM-98.patch](https://raw.github.com/alexec/maven-vbox-plugin-example/master/MRPM-98.patch):

	svn co https://svn.codehaus.org/mojo/trunk/mojo/rpm-maven-plugin
	cd rpm-maven-plugin
	patch -p0 -i MRPM-98.patch
	mvn install

Tutorial
--
Firstly we need to create the Maven project, we'll use a simple archetype:

    mvn archetype:generate -DgroupId=com.alexecollins -DartifactId=hello-world -DarchetypeArtifactId=maven-archetype-webapp -DinteractiveMode=false
    cd hello-world

To the POM we need to add the plugin to create the VM:

    <plugin>
        <groupId>com.alexecollins.vbox</groupId>
        <artifactId>vbox-maven-plugin</artifactId>
        <version>1.0.0-SNAPSHOT</version>
        <executions>
            <execution>
                <goals>
                    <goal>clean</goal>
                    <goal>provision</goal>
                    <goal>start</goal>
                    <goal>stop</goal>
                </goals>
            </execution>
        </executions>
    </plugin>

We need to find a template VirtualMachine

    mvn vbox:list-definitions

This will list the one we want, CentOS 6.3, to we'll get the added to our source:

    mvn vbox:create-definition -Dvbox.name=CentOS_6_3

Now we have the box's definition in src/main/vbox/CentOS_6_3. Rename it to "app1" for now. I want to create a host only network so that it's easy to access and use the VMs, and it's good for SSH to and save setting up port forward we might want to share. Open VirtualBox.xml and replace the adapter with:

    <Adapter slot="0">
         <HostOnlyInterface name="vboxnet0"/>
     </Adapter>
     <Adapter slot="1">
         <NAT/>
     </Adapter>

Keeping the NAT adapter allows the VM to speak to the Internet. It's quite feasible to create one VM (a gateway) that serve RPMs using FTP and provides SSH tunnelling.

Note that the name of the adapter differs between OSs, check in the preferences to find out (and to create one if needs be).

We need to update the Provision.xml to remove the port forward, delete this line:

    <PortForward hostport="10022" guestport="22"/>

We also need to update the kick-start (ks.cfc) to enable the new adapter, we're going to give it an IP address in the same sub-net and the host-only network.

    network --device eth0 --onboot yes --bootproto static --ip 192.168.56.2 --noipv6
    network --device eth1 --onboot yes --bootproto dhcp --noipv6

And we'll disable the firewall so our HTTP connection can get though (not very secure, but good for now):

    firewall --disabled

We're going to deploy the webapp onto Tomcat 6. This Cent OS install is pretty basic. Modify the post-install.sh and add the following lines to install and start Tomcat before the power off:

    yum -y install tomcat6
    chkconfig tomcat6 on

We can test the setting easily, firstly create and provision the box:

    mvn vbox:provision

It'll want to download the ISO, so you may want to go and get a cup of tea! Once that's complete, start the VM:

    mvn vbox:start

Note: the default setting starts the VM in headless mode. You can switch to gui mode by changing Profile.xml:

    <Profile xmlns="http://www.alexecollins.com/vbox/profile" type="gui">

See if you can hit Apache by opening:

    http://192.168.56.2:8080/

You can stop the box:

    mvn vbox:stop

A detail, but we need to let the plugin know when the box is ready to use (much like the Cargo plugin). Add this line to Profile.xml:

    <ping url="socket://192.168.56.2:8080/"/>

Next we need to build the war into an RPM:

    <plugin>
        <groupId>org.codehaus.mojo</groupId>
        <artifactId>rpm-maven-plugin</artifactId>
        <version>2.1-alpha-3-SNAPSHOT</version>
        <configuration>
            <group>Alex Collins</group>
            <copyright>-</copyright>
            <targetOS>linux</targetOS>
            <targetVendor>redhat</targetVendor>
            <mappings>
                <mapping>
                    <directory>/usr/share/tomcat6/webapps/${project.artifactId}</directory>
                    <sources>
                        <source>
                            <location>target/${project.artifactId}</location>
                        </source>
                    </sources>
                </mapping>
            </mappings>
            <requires>
                <require>tomcat6</require>
            </requires>
        </configuration>
        <executions>
            <execution>
                <goals>
                    <goal>rpm</goal>
                </goals>
            </execution>
        </executions>
    </plugin>

If you're not familiar with RPMs, you might want to read up on this, but in summary it creates an RPM that copies the directory into Tomcat's webapp directory. Note the version is alpha-3, this includes the fix above. alpha-2 might serve your purpose if you host OS is Linux.

Finally, we need to deploy the RPM onto the box, we'll keep it simple and use Ant, so add this

    <plugin>
        <artifactId>maven-antrun-plugin</artifactId>
        <version>1.7</version>
        <executions>
            <execution>
                <id>deploy-rpm-to-vbox</id>
                <phase>pre-integration-test</phase>
                <goals>
                    <goal>run</goal>
                </goals>
                <configuration>
                    <target>
                        <!-- note we force trust as new VMs will get new MAC etc. -->
                        <scp todir="root@192.168.56.2:/root" password="keenbrick" trust="true">
                            <fileset dir="${project.build.directory}/rpm/${project.artifactId}/RPMS/noarch"/>
                        </scp>
                        <sshexec host="192.168.56.2" username="root" password="keenbrick" trust="true"
                                 command="yum -y install *.rpm"/>
                    </target>
                </configuration>
            </execution>
        </executions>
        <dependencies>
            <!-- for SSH/SCP -->
            <dependency>
                <groupId>org.apache.ant</groupId>
                <artifactId>ant-jsch</artifactId>
                <version>1.8.4</version>
            </dependency>
        </dependencies>
    </plugin>

We can test this by executing:

    mvn pre-integration-test

vbox:start is bound to this phase, so it'll package the war, provision the vox, and deploy the war. Check out your handy work:

    http://192.168.56.2:8080/maven-vbox-plugin-example

Next, an integration test, so enable add the fail-safe plugin to you POM and create an execution for the standard goals. Create a test named HelloWorldIT, with a single method:

    @Test
    public void testHelloWorld() throws Exception {
        assertTrue(Jsoup.connect("http://192.168.56.2:8080/maven-vbox-plugin-example").get().html().contains("Hello World"));
    }

You'll need to add JSoup and JUnit to the POM dependencies. Finally, test the whole thing:

    mvn verify

Now, celebrate! OK, I've obviously not covered a number of topics, so here are some links to get you started.

- http://mojo.codehaus.org/rpm-maven-plugin/index.html - Maven RPM Plugin Docs
- http://rpm5.org/files/rpm/rpm-5.1/BINARY/ - rpmbuild for OS-X
- http://www.slideshare.net/actionjackx/automated-java-deployments-with-rpm - good slides on this same topic
- http://stnor.wordpress.com/category/devops/ - good blog post on this same topic
- https://blogs.oracle.com/fatbloke/entry/networking_in_virtualbox1 - guide to VirtualBox networking
- http://www.alexecollins.com/?q=content/tips-writing-maven-plugins - my tips for writing Maven plugins
