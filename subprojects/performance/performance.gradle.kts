plugins {
    id("gradlebuild.internal.java")
    id("gradlebuild.performance-test")
    id("gradlebuild.performance-templates")
}

dependencies {
    performanceTestImplementation(project(":baseServices"))
    performanceTestImplementation(project(":core"))
    performanceTestImplementation(project(":modelCore"))
    performanceTestImplementation(project(":coreApi"))
    performanceTestImplementation(project(":buildOption"))
    performanceTestImplementation(libs.slf4j_api)
    performanceTestImplementation(libs.commons_io)
    performanceTestImplementation(libs.commons_compress)
    performanceTestImplementation(libs.jetty)
    performanceTestImplementation(testFixtures(project(":toolingApi")))

    performanceTestDistributionRuntimeOnly(project(":distributionsFull")) {
        because("All Gradle features have to be available.")
    }
    performanceTestLocalRepository(project(":toolingApi")) {
        because("IDE tests use the Tooling API.")
    }
}
