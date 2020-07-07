/*
 * Copyright 2014 the original author or authors.
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
plugins {
    id("gradlebuild.distribution.api-java")
}

dependencies {
    api(project(":resources"))
    implementation(project(":baseServices"))
    implementation(project(":coreApi"))
    implementation(project(":core"))
    implementation(project(":modelCore"))
    implementation(project(":logging"))

    implementation(libs.commons_httpclient)
    implementation(libs.slf4j_api)
    implementation(libs.jcl_to_slf4j)
    implementation(libs.jcifs)
    implementation(libs.guava)
    implementation(libs.commons_lang)
    implementation(libs.commons_io)
    implementation(libs.xerces)
    implementation(libs.nekohtml)

    testImplementation(project(":internalIntegTesting"))
    testImplementation(libs.jetty)
    testImplementation(testFixtures(project(":core")))
    testImplementation(testFixtures(project(":logging")))

    testFixturesImplementation(project(":baseServices"))
    testFixturesImplementation(project(":logging"))
    testFixturesImplementation(project(":internalIntegTesting"))
    testFixturesImplementation(libs.slf4j_api)

    integTestDistributionRuntimeOnly(project(":distributionsCore"))
}
