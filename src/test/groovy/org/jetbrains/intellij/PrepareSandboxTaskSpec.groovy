package org.jetbrains.intellij

import java.util.zip.ZipFile

class PrepareSandboxTaskSpec extends IntelliJPluginSpecBase {
    def 'prepare sandbox for two plugins'() {
        given:
        writeJavaFile()
        pluginXml << """\
            <idea-plugin>
              <id>org.intellij.test.plugin</id>
              <name>Test Plugin</name>
              <version>1.0</version>
              <vendor url="https://jetbrains.com">JetBrains</vendor>
              <description>test plugin</description>
              <change-notes/>
            </idea-plugin>""".stripIndent()

        buildFile << """\
            version='0.42.123'
            intellij.pluginName = 'myPluginName'
            intellij.plugins = [project('nestedProject')]
            """.stripIndent()
        file('settings.gradle') << "include 'nestedProject'"
        file('nestedProject/build.gradle') << """
            apply plugin: 'org.jetbrains.intellij'
            intellij {
                version = '14.1.3'
                downloadSources = false
                intellijRepo = '$intellijRepo'
                pluginName = 'myNestedPluginName'
            }
            version='0.42.123'"""
        file('nestedProject/src/main/java/NestedAppFile.java') << "class NestedAppFile{}"
        file('nestedProject/src/main/resources/META-INF/plugin.xml') << pluginXml.text

        when:
        build(":$IntelliJPlugin.PREPARE_SANDBOX_TASK_NAME")

        then:
        collectPaths(sandbox) == ['/plugins/myPluginName/lib/projectName-0.42.123.jar',
                                  '/plugins/myNestedPluginName/lib/nestedProject-0.42.123.jar',
                                  '/config/options/updates.xml'] as Set

        def jar = new File(sandbox, '/plugins/myPluginName/lib/projectName-0.42.123.jar')
        collectPaths(new ZipFile(jar)) == ['META-INF/', 'META-INF/MANIFEST.MF', 'App.class', 'META-INF/plugin.xml'] as Set

        def nestedProjectJar = new File(sandbox, '/plugins/myNestedPluginName/lib/nestedProject-0.42.123.jar')
        collectPaths(new ZipFile(nestedProjectJar)) == ['META-INF/', 'META-INF/MANIFEST.MF', 'NestedAppFile.class',
                                                        'META-INF/plugin.xml'] as Set
    }

    def 'prepare sandbox task without plugin_xml'() {
        given:
        writeJavaFile()
        buildFile << """\
            version='0.42.123'
            intellij { 
                pluginName = 'myPluginName' 
                plugins = ['copyright'] 
            }
            dependencies { 
                compile 'joda-time:joda-time:2.8.1'
            }\
            """.stripIndent()

        when:
        build(IntelliJPlugin.PREPARE_SANDBOX_TASK_NAME)

        then:
        collectPaths(sandbox) == ['/plugins/myPluginName/lib/projectName-0.42.123.jar',
                                  '/plugins/myPluginName/lib/joda-time-2.8.1.jar',
                                  '/config/options/updates.xml'] as Set
    }

    def 'prepare sandbox task'() {
        given:
        writeJavaFile()
        file('src/main/resources/META-INF/other.xml') << '<idea-plugin></idea-plugin>'
        file('src/main/resources/META-INF/nonIncluded.xml') << '<idea-plugin></idea-plugin>'
        pluginXml << '<idea-plugin version="2"><depends config-file="other.xml"/></idea-plugin>'
        buildFile << """\
            version='0.42.123'
            intellij { 
                pluginName = 'myPluginName' 
                plugins = ['copyright'] 
            }
            dependencies { 
                compile 'joda-time:joda-time:2.8.1'
            }\
            """.stripIndent()

        when:
        build(IntelliJPlugin.PREPARE_SANDBOX_TASK_NAME)

        then:
        collectPaths(sandbox) == ['/plugins/myPluginName/lib/projectName-0.42.123.jar',
                                  '/plugins/myPluginName/lib/joda-time-2.8.1.jar',
                                  '/config/options/updates.xml'] as Set

        def jar = new ZipFile(new File(sandbox, '/plugins/myPluginName/lib/projectName-0.42.123.jar'))
        collectPaths(jar) == ['META-INF/', 'META-INF/MANIFEST.MF', 'App.class', 'META-INF/nonIncluded.xml',
                              'META-INF/other.xml', 'META-INF/plugin.xml'] as Set
        fileText(jar, 'META-INF/plugin.xml') == """\
            <idea-plugin version="2">
              <version>0.42.123</version>
              <idea-version since-build="141.1010" until-build="141.*"/>
              <depends config-file="other.xml"/>
            </idea-plugin>""".stripIndent()
    }

    def 'prepare sandbox with external jar-type plugin'() {
        given:
        writeJavaFile()
        pluginXml << '<idea-plugin version="2"></idea-plugin>'
        buildFile << """\
            intellij {
                plugins = ['org.jetbrains.postfixCompletion:0.8-beta']
                pluginName = 'myPluginName'
            }
            """.stripIndent()
        when:
        build(IntelliJPlugin.PREPARE_SANDBOX_TASK_NAME)

        then:
        collectPaths(sandbox) == ['/plugins/intellij-postfix.jar',
                                  '/plugins/myPluginName/lib/projectName.jar',
                                  '/config/options/updates.xml'] as Set
    }

    def 'prepare sandbox with external zip-type plugin'() {
        given:
        writeJavaFile()
        pluginXml << '<idea-plugin version="2"></idea-plugin>'
        buildFile << """\
            intellij {
                plugins = ['org.intellij.plugins.markdown:8.0.0.20150929']
                pluginName = 'myPluginName'
            }
            """.stripIndent()
        when:
        build(IntelliJPlugin.PREPARE_SANDBOX_TASK_NAME)

        then:
        collectPaths(sandbox) == ['/plugins/myPluginName/lib/projectName.jar',
                                  '/plugins/markdown/lib/default.css',
                                  '/plugins/markdown/lib/markdown.jar',
                                  '/plugins/markdown/lib/darcula.css',
                                  '/config/options/updates.xml',
                                  '/plugins/markdown/lib/kotlin-runtime.jar',
                                  '/plugins/markdown/lib/Loboevolution.jar',
                                  '/plugins/markdown/lib/intellij-markdown.jar',
                                  '/plugins/markdown/lib/kotlin-reflect.jar'] as Set
    }

    def 'prepare sandbox with plugin dependency with classes directory'() {
        given:
        def plugin = createPlugin()
        writeJavaFile()
        pluginXml << '<idea-plugin version="2"></idea-plugin>'
        buildFile << """\
            intellij {
                plugins = ['${adjustWindowsPath(plugin.canonicalPath)}']
                pluginName = 'myPluginName'
            }
            """.stripIndent()

        when:
        build(IntelliJPlugin.PREPARE_SANDBOX_TASK_NAME)

        then:
        collectPaths(sandbox) == ['/plugins/myPluginName/lib/projectName.jar',
                                  '/config/options/updates.xml',
                                  "/plugins/$plugin.name/classes/A.class",
                                  "/plugins/$plugin.name/classes/someResources.properties",
                                  "/plugins/$plugin.name/META-INF/plugin.xml"] as Set
    }

    private static def createPlugin() {
        def plugin = File.createTempDir()
        new File(plugin, 'classes/').mkdir()
        new File(plugin, 'META-INF/').mkdir()
        new File(plugin, 'classes/A.class').createNewFile()
        new File(plugin, 'classes/someResources.properties').createNewFile()
        new File(plugin, 'META-INF/plugin.xml') << """\
            <idea-plugin>
              <id>$plugin.name</id>
              <name>Test Plugin</name>
              <version>1.0</version>
              <idea-version since-build="141.1010" until-build="141.*"/>
              <vendor url="https://jetbrains.com">JetBrains</vendor>
              <description>test plugin</description>
              <change-notes/>
            </idea-plugin>""".stripIndent()
        return plugin
    }

    def 'prepare custom sandbox task'() {
        given:
        writeJavaFile()
        file('src/main/resources/META-INF/other.xml') << '<idea-plugin></idea-plugin>'
        file('src/main/resources/META-INF/nonIncluded.xml') << '<idea-plugin></idea-plugin>'
        pluginXml << '<idea-plugin version="2"><depends config-file="other.xml"/></idea-plugin>'
        def sandboxPath = adjustWindowsPath("$dir.root.absolutePath/customSandbox")
        buildFile << """\
            version='0.42.123'
            intellij { 
                pluginName = 'myPluginName' 
                plugins = ['copyright'] 
                sandboxDirectory = '$sandboxPath'
            }
            dependencies { 
                compile 'joda-time:joda-time:2.8.1'
            }\
            """.stripIndent()

        when:
        build(IntelliJPlugin.PREPARE_SANDBOX_TASK_NAME)

        def sandbox = new File(sandboxPath)

        then:
        collectPaths(sandbox) == ['/plugins/myPluginName/lib/projectName-0.42.123.jar',
                                  '/plugins/myPluginName/lib/joda-time-2.8.1.jar',
                                  '/config/options/updates.xml'] as Set
    }

    def 'use gradle project name if plugin name is not defined'() {
        given:
        pluginXml << '<idea-plugin version="2"></idea-plugin>'

        when:
        build(IntelliJPlugin.PREPARE_SANDBOX_TASK_NAME)

        then:
        collectPaths(sandbox) == ["/plugins/projectName/lib/projectName.jar", '/config/options/updates.xml'] as Set
    }

    def 'disable ide update without updates.xml'() {
        given:
        pluginXml << '<idea-plugin version="2"></idea-plugin>'

        when:
        build(IntelliJPlugin.PREPARE_SANDBOX_TASK_NAME)

        then:
        assertFileContent(new File(buildDirectory, "$IntelliJPlugin.DEFAULT_SANDBOX/config/options/updates.xml"), '''\
            <application>
              <component name="UpdatesConfigurable">
                <option name="CHECK_NEEDED" value="false"/>
              </component>
            </application>''')
    }

    def 'disable ide update without updates component'() {
        given:
        pluginXml << '<idea-plugin version="2"></idea-plugin>'
        def updatesFile = new File(directory("build/$IntelliJPlugin.DEFAULT_SANDBOX/config/options"), 'updates.xml')
        updatesFile.text = '''\
            <application>
              <component name="SomeOtherComponent">
                <option name="SomeOption" value="false"/>
              </component>
            </application>'''.stripIndent()

        when:
        build(IntelliJPlugin.PREPARE_SANDBOX_TASK_NAME)

        then:
        assertFileContent(new File(buildDirectory, "$IntelliJPlugin.DEFAULT_SANDBOX/config/options/updates.xml"), '''\
            <application>
              <component name="SomeOtherComponent">
                <option name="SomeOption" value="false"/>
              </component>
              <component name="UpdatesConfigurable">
                <option name="CHECK_NEEDED" value="false"/>
              </component>
            </application>''')
    }

    def 'disable ide update without check_needed option'() {
        given:
        pluginXml << '<idea-plugin version="2"></idea-plugin>'
        def updatesFile = new File(directory("build/$IntelliJPlugin.DEFAULT_SANDBOX/config/options"), 'updates.xml')
        updatesFile.text = '''\
            <application>
              <component name="UpdatesConfigurable">
                <option name="SomeOption" value="false"/>
              </component>
            </application>'''.stripIndent()

        when:
        build(IntelliJPlugin.PREPARE_SANDBOX_TASK_NAME)

        then:
        assertFileContent(new File(buildDirectory, "$IntelliJPlugin.DEFAULT_SANDBOX/config/options/updates.xml"), '''\
            <application>
              <component name="UpdatesConfigurable">
                <option name="SomeOption" value="false"/>
                <option name="CHECK_NEEDED" value="false"/>
              </component>
            </application>''')
    }

    def 'disable ide update without value attribute'() {
        given:
        pluginXml << '<idea-plugin version="2"></idea-plugin>'
        def updatesFile = new File(directory("build/$IntelliJPlugin.DEFAULT_SANDBOX/config/options"), 'updates.xml')
        updatesFile.text = '''\
            <application>
              <component name="UpdatesConfigurable">
                <option name="CHECK_NEEDED"/>
              </component>
            </application>'''.stripIndent()

        when:
        build(IntelliJPlugin.PREPARE_SANDBOX_TASK_NAME)

        then:
        assertFileContent(new File(buildDirectory, "$IntelliJPlugin.DEFAULT_SANDBOX/config/options/updates.xml"), '''\
            <application>
              <component name="UpdatesConfigurable">
                <option name="CHECK_NEEDED" value="false"/>
              </component>
            </application>''')
    }

    def 'disable ide update'() {
        given:
        pluginXml << '<idea-plugin version="2"></idea-plugin>'
        def updatesFile = new File(directory("build/$IntelliJPlugin.DEFAULT_SANDBOX/config/options"), 'updates.xml')
        updatesFile.text = '''\
            <application>
              <component name="UpdatesConfigurable">
                <option name="CHECK_NEEDED" value="true"/>
              </component>
            </application>'''.stripIndent()

        when:
        build(IntelliJPlugin.PREPARE_SANDBOX_TASK_NAME)

        then:
        assertFileContent(new File(buildDirectory, "$IntelliJPlugin.DEFAULT_SANDBOX/config/options/updates.xml"), '''\
            <application>
              <component name="UpdatesConfigurable">
                <option name="CHECK_NEEDED" value="false"/>
              </component>
            </application>''')
    }

    def 'replace jar on version changing'() {
        given:
        pluginXml << '<idea-plugin version="2"></idea-plugin>'
        buildFile << 'version=\'0.42.123\'\n'
        build(IntelliJPlugin.PREPARE_SANDBOX_TASK_NAME)
        buildFile << 'version=\'0.42.124\'\n'

        when:
        build(IntelliJPlugin.PREPARE_SANDBOX_TASK_NAME)

        then:
        collectPaths(sandbox) == ['/plugins/projectName/lib/projectName-0.42.124.jar',
                                  '/config/options/updates.xml'] as Set
    }

    private File getSandbox() {
        return new File(buildDirectory, IntelliJPlugin.DEFAULT_SANDBOX)
    }
}
