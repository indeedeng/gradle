/*
 * Copyright 2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.api.internal.indeed

import org.gradle.integtests.fixtures.AbstractDependencyResolutionTest
import org.gradle.integtests.fixtures.resolve.ResolveTestFixture
import org.gradle.test.fixtures.maven.MavenModule

class IndeedResolverCoreTest extends AbstractDependencyResolutionTest {
    ResolveTestFixture resolve

    def setup() {
        resolve = new ResolveTestFixture(buildFile, "runtime")
        resolve.expectDefaultConfiguration("runtime")
        settingsFile << "rootProject.name = 'indeedResolveCore'"
        resolve.prepare()
        buildFile << """
apply plugin: 'java-library'
repositories {
    maven { url "${mavenRepo.uri}" }
}"""
    }

    def 'every dependency in maven pom is treated as forced'() {
        /**
         * baz 1.0.0
         *    |-foo 1.0.0
         *    |-bar 1.0.0
         *        |-foo 1.0.1
         *
         *  In the OSS Gradle, the dependency resolution should get foo 1.0.1 as foo 1.0.1 is the latest one.
         *  However, in the Indeed Gradle, the dependency resolution will get foo 1.0.0 as the baz 1.0.0 will force to use foo 1.0.0
         */
        MavenModule foo1_0_0 = mavenRepo.module('com.indeed', 'foo', '1.0.0').publish()
        MavenModule foo1_0_1 = mavenRepo.module('com.indeed', 'foo', '1.0.1').publish()

        MavenModule bar1_0_0 = mavenRepo.module('com.indeed', 'bar', '1.0.0')
            .dependsOn(foo1_0_1)
            .publish()

        mavenRepo.module('com.indeed', 'baz', '1.0.0')
            .dependsOn(bar1_0_0)
            .dependsOn(foo1_0_0)
            .publish()


        buildFile << """
dependencies {
    compile 'com.indeed:baz:1.0.0'
}
"""
        when:
        succeeds 'checkDep', 'dependencyInsight', '--configuration', 'runtime', '--dependency', 'foo'

        then:
        outputContains("""
com.indeed:foo:1.0.0
\\--- com.indeed:baz:1.0.0
     \\--- runtime""")
        outputContains("""
com.indeed:foo:1.0.1 -> 1.0.0
\\--- com.indeed:bar:1.0.0
     \\--- com.indeed:baz:1.0.0
          \\--- runtime""")

        resolve.expectGraph {
            root(':', ':indeedResolveCore:') {
                module("com.indeed:baz:1.0.0") {
                    module("com.indeed:bar:1.0.0") {
                        edge('com.indeed:foo:1.0.1', 'com.indeed:foo:1.0.0') {
                            byConstraint("com.indeed:foo:1.0.1 -> com.indeed:foo:1.0.0 (<override> in com.indeed:baz:1.0.0)")
                        }
                    }
                    module("com.indeed:foo:1.0.0")
                }
            }
        }
    }

    def 'sub version is not treat as compatible'() {
        /**
         * foo 1.+
         * bar 1.0.0
         *    |-foo 1.0.0
         *
         *  In the OSS Gradle, the dependency resolution should get foo 1.0.0 as foo 1.0.0 is within the version range [1.+, )
         *  However, in the Indeed Gradle, the dependency resolution will get foo 1.0.1
         *      as foo:1.+ indicate the latest version in this range which mean foo:1.0.1 here.
         */
        MavenModule foo1_0_0 = mavenRepo.module('com.indeed', 'foo', '1.0.0').publish()
        mavenRepo.module('com.indeed', 'foo', '1.0.1').publish()

        mavenRepo.module('com.indeed', 'bar', '1.0.0')
            .dependsOn(foo1_0_0)
            .publish()

        buildFile << """
dependencies {
    compile 'com.indeed:bar:1.0.0'
    compile 'com.indeed:foo:1.+'
}
"""
        when:
        succeeds 'checkDep', 'dependencyInsight', '--configuration', 'compileClasspath', '--dependency', 'foo'

        then:
        outputContains("""
   Selection reasons:
      - By conflict resolution : between versions 1.0.0 and 1.0.1""")
        outputContains("""
com.indeed:foo:1.0.0 -> 1.0.1
\\--- com.indeed:bar:1.0.0
     \\--- compileClasspath""")
        outputContains("""
com.indeed:foo:1.+ -> 1.0.1
\\--- compileClasspath""")

        resolve.expectGraph {
            root(':', ':indeedResolveCore:') {
                module("com.indeed:bar:1.0.0") {
                    edge('com.indeed:foo:1.0.0', 'com.indeed:foo:1.0.1') {
                        byConflictResolution("between versions 1.0.0 and 1.0.1")
                    }
                }
                edge('com.indeed:foo:1.+', 'com.indeed:foo:1.0.1')
            }
        }
    }

    def 'version range is not treat as compatible'() {
        /**
         * foo [1.0, 2)
         * bar 1.0.0
         *    |-foo 1.0.0
         *
         *  In the OSS Gradle, the dependency resolution should get foo 1.0.0 as foo 1.0.0 is within the version range [1.0, 2)
         *  However, in the Indeed Gradle, the dependency resolution will get foo 1.0.1
         *      as foo:[1.0, 2) indicate the latest version in this range which mean foo:1.0.1 here.
         */
        MavenModule foo1_0_0 = mavenRepo.module('com.indeed', 'foo', '1.0.0').publish()
        mavenRepo.module('com.indeed', 'foo', '1.0.1').publish()
        mavenRepo.module('com.indeed', 'foo', '2.0.0').publish()

        mavenRepo.module('com.indeed', 'bar', '1.0.0')
            .dependsOn(foo1_0_0)
            .publish()

        buildFile << """
dependencies {
    compile 'com.indeed:bar:1.0.0'
    compile 'com.indeed:foo:[1.0, 2)'
}
"""
        when:
        succeeds 'checkDep', 'dependencyInsight', '--configuration', 'compileClasspath', '--dependency', 'foo'

        then:
        outputContains("""
   Selection reasons:
      - Was requested : didn't match version 2.0.0
      - By conflict resolution : between versions 1.0.0 and 1.0.1""")
        outputContains("""
com.indeed:foo:1.0.0 -> 1.0.1
\\--- com.indeed:bar:1.0.0
     \\--- compileClasspath""")
        outputContains("""
com.indeed:foo:[1.0, 2) -> 1.0.1
\\--- compileClasspath""")

        resolve.expectGraph {
            root(':', ':indeedResolveCore:') {
                module("com.indeed:bar:1.0.0") {
                    edge('com.indeed:foo:1.0.0', 'com.indeed:foo:1.0.1') {
                        byConflictResolution("between versions 1.0.0 and 1.0.1")
                    }
                }
                edge('com.indeed:foo:[1.0, 2)', 'com.indeed:foo:1.0.1')
            }
        }
    }
}
