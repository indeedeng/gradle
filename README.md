## How to contribute to Gradle Core
Making new changes to our fork of Gradle Core is strongly discouraged! Please make sure we have alternative available before proposing contributions to our fork of Gradle Core.

Steps:
* File a GRADLE Jira ticket.
* Wrap the change you make to the Gradle Core with following format:
    ```
    // BEGIN_INDEED <Jira Ticket>
    <Your Change>
    // END_INDEED
    ```
* Document the change you made to Gradle in the `Indeed Gradle Patch` section of the README file.

## Indeed Gradle Patches

### ivy.xml enhancements:
* Tracking Issue: [GRADLE-432](https://bugs.indeed.com/browse/GRADLE-432)
* Commits: 
[5b39004](https://code.corp.indeed.com/gradle/gradle/commit/5b390045fa12fb35b99bcaef8c59056dfa32fc26)
[5314648](https://code.corp.indeed.com/gradle/gradle/commit/531464820abd025ee1fd37793317e0821d17f48c)
[6b334d7](https://code.corp.indeed.com/gradle/gradle/commit/6b334d766ca554a983c5023cc3189ea9f67ebc8d)
[b4982c7](https://code.corp.indeed.com/gradle/gradle/commit/b4982c72ce2102c85b9517bfe51fdd0cb7c58cf4)
[9270304](https://code.corp.indeed.com/gradle/gradle/commit/92703046d462015d1a978f75ecf7d5c6ffe572e8)
[937c1f2](https://code.corp.indeed.com/gradle/gradle/commit/937c1f274865559f050d8aa1ed25cb9332ae3a80)
[a558bf6](https://code.corp.indeed.com/gradle/gradle/commit/a558bf6b12c53f7808f1addbe74737bdfc42e7cd)
[661b9ba](https://code.corp.indeed.com/gradle/gradle/commit/661b9ba2216a93996c988f0f9c3347353d28d872)
[3d6eadf](https://code.corp.indeed.com/gradle/gradle/commit/3d6eadf7bbce3c99df651f2d38e6099f0d68d1b7)
[704b90d](https://code.corp.indeed.com/gradle/gradle/commit/704b90d6edd8c614c62c63db8fb00df765beac76)
[42e45ae](https://code.corp.indeed.com/gradle/gradle/commit/42e45aec999a5e4f03e57445e903ffd60b7afdc3)
[6017a17](https://code.corp.indeed.com/gradle/gradle/commit/6017a172067cd37b671951351823bd657d4f9745)
[d0fe2c8](https://code.corp.indeed.com/gradle/gradle/commit/d0fe2c80d958a857fb96696c527ac34d2d0e2ad8)
[955da5f](https://code.corp.indeed.com/gradle/gradle/commit/955da5f0270defd9a96368e4e6b707d54f133b1b)
[8676483](https://code.corp.indeed.com/gradle/gradle/commit/8676483c34dfb4c4cfc67646ffe30189265f1033)
[9218271](https://code.corp.indeed.com/gradle/gradle/commit/9218271a8464f74d901b889f362c391bb3326073)
[47d6083](https://code.corp.indeed.com/gradle/gradle/commit/47d60835c71a81812a0f858fbdc5f4d9fe5f252d)
* Cause: 
    * The OSS Gradle doesn't support the `force="true"` attribute specified in the [`<dependency>`](https://ant.apache.org/ivy/history/2.5.0/ivyfile/dependency.html).
    * The OSS Gradle doesn't support [`<override>`](https://ant.apache.org/ivy/history/2.5.0/ivyfile/override.html)
    * The OSS Gradle doesn't support [Ivy conflict manager](https://ant.apache.org/ivy/history/2.5.0/settings/conflict-managers.html).
    * For the configuration mapping such as `sources->sources()`, OSS Gradle will try to find the `source` configuration first. 
If the Gradle failed to find the `source` configuration, it will try to resolve an empty configuration and fail. The desired behavior for us is to include nothing instead of failing.
* Summary:
    * Support for [`force="true"`](https://ant.apache.org/ivy/history/2.5.0/ivyfile/dependency.html)
    * Support for [`<override>`](https://ant.apache.org/ivy/history/2.5.0/ivyfile/override.html)
    * Treat [`<conflict>`](https://ant.apache.org/ivy/history/2.5.0/settings/conflict-managers.html) as [`<override>`](https://ant.apache.org/ivy/history/2.5.0/ivyfile/override.html)
    * Support for empty ("") configuration

### pom.xml enhancements
* Tracking Issue: [GRADLE-433](https://bugs.indeed.com/browse/GRADLE-433)
* Commits: [5314648](https://code.corp.indeed.com/gradle/gradle/commit/531464820abd025ee1fd37793317e0821d17f48c)
* Cause: In the Maven world, dependencies specified in the `pom.xml` file are treated as `force=true`.
Ivy follows this behavior when resolving a Maven artifact, but Gradle does not.
* Summary: All dependencies in Maven poms are treated as "forced".
(Used to indicate that this revision must be used in case of conflicts. This only works for direct dependencies, and not transitive ones.)

### Dependency exclusions are rewritten using rewrite rules
* Tracking Issue: [GRADLE-436](https://bugs.indeed.com/browse/GRADLE-436)
* Commits: 
[49889dd](https://code.corp.indeed.com/gradle/gradle/commit/49889dd8ae554bc7719bad696f50479b197e6fc3)
[b4982c7](https://code.corp.indeed.com/gradle/gradle/commit/b4982c72ce2102c85b9517bfe51fdd0cb7c58cf4)
[3bd1920](https://code.corp.indeed.com/gradle/gradle/commit/3bd192076ddd883263431e59b4f788e596f8cf97)
[b4982c7](https://code.corp.indeed.com/gradle/gradle/commit/b4982c72ce2102c85b9517bfe51fdd0cb7c58cf4)
[cb80a24](https://code.corp.indeed.com/gradle/gradle/commit/cb80a2497d2fee1cb1161fc3753006590f267f3e)
[6017a17](https://code.corp.indeed.com/gradle/gradle/commit/6017a172067cd37b671951351823bd657d4f9745)
* Cause: We want to incorporate [`<exclude>`](https://ant.apache.org/ivy/history/2.5.0/ivyfile/artifact-exclude.html) into our `override-rewrite` loop in `IndeedResolveCore` 
so that we can actually exclude the renamed artifacts.
* Summary: Rewrite the OSS `exclude` in our `IndeedResolveCore` and remove the original logic from the `NodeState` and `EdgeState`.

### Disabled "SelectorState" caching in the "ResolveState"
* Tracking Issue: [GRADLE-64](https://bugs.indeed.com/browse/GRADLE-64)
* Commits: [ee20713](https://code.corp.indeed.com/gradle/gradle/commit/ee20713a3e10afe5cced52a493be7f7778dcaf39), [88659cd](https://code.corp.indeed.com/gradle/gradle/commit/88659cd0b15a62c2860740d86cacb7a31c27cdbf)
* Cause: Due to overrides and excludes, `ResolveState` must cache based on both requested AND selected id,
since the selected id may change during the resolve process in indeed gradle.
* Summary: prevent the selected version for any given requested version from changing during a resolve 
(which can happen in indeed Gradle, as incoming edges change the `<overrides>` applied to a dependency)

### Never do dependency substitution for internal dependency.
* Tracking Issue: [GRADLE-137](https://bugs.indeed.com/browse/GRADLE-137)
* Commits: [bf3909e](https://code.corp.indeed.com/gradle/gradle/commit/bf3909e4d015803fd662785ebd818798139c150c)
* Cause: Indeed [dependency rewrites](https://code.corp.indeed.com/gradle/common-gradle-plugin/blob/5d9b65e55e85d0b7291a5559422d84b99b7cb1c8/src/main/java/com/indeed/gradleplugin/repos/repo/IndeedArtifactRewriter.java#L125) work by repeatedly applying `<overrides>` and `<exclude>` until the result doesn't change. OSS Gradle does the rewrite once based on the assumption that
"the local project would likely be using the newest names already", which is not true with projects at Indeed. 
This may cause an infinite loop if the dependency is an inter-project dependency (a sub module)
* Summary: Skip the [`substitution`](https://docs.gradle.org/current/dsl/org.gradle.api.artifacts.DependencySubstitutions.html) if the required dependency is a sub module in the project.

### Add support to get composite build substitutions from a project property rather than the project name
* Tracking Issue: [GRADLE-448](https://bugs.indeed.com/browse/GRADLE-448)
* Commits: [afd1ed6](https://code.corp.indeed.com/gradle/gradle/commit/afd1ed6e91c3df846557ff6b8ba49e7a3205bba5)
* Cause: When gradle is connecting dependencies between projects in a multi-project [`composite build`](https://docs.gradle.org/current/userguide/composite_builds.html),
it usually looks at the [`project group`](https://docs.gradle.org/current/dsl/org.gradle.api.Project.html#org.gradle.api.Project:group) and [`project name`](https://docs.gradle.org/current/dsl/org.gradle.api.Project.html#org.gradle.api.Project:name),
and assumes that they are equal to the maven group and maven name, but that isn't the case for projects at Indeed (we don't set them, so the project group is empty and project name is equal to the project directory name). Instead, we set extra project properties `indeed.composite.build.group` and `indeed.composite.build.name` in `IndeedPublishPlugin` in the `common-gradle-plugin` and
read those properties in Gradle Core and use them as project group and name for imported project.
* Summary: Read the extra project properties `indeed.composite.build.group` and `indeed.composite.build.name` in the included build. If the property exists, use them to register the project. This allows the indeed publish plugin to specify what artifact the project is responsible for in a composite build.

### Made `cacheDynamicVersionsFor` apply to empty version list responses as well.
* Tracking issue: [GRADLE-447](https://bugs.indeed.com/browse/GRADLE-447)
* Commits: [47388ec](https://code.corp.indeed.com/gradle/gradle/commit/47388ec9a352c46fccf666fb4ab413164f75ffa6)
* Cause: We are in the process of migrating modules from Ivy to Maven. If Gradle cached an empty version list from maven for a dependency, it will never check for new (migrated) versions.
OSS Gradle decided to not fix this one (see [gradle/gradle #7601](https://github.com/gradle/gradle/issues/7601)).
* Summary: Prevent infinite caching of empty version lists from repos.

### Makes Gradle use the correct artifact pattern to search for sources artifact in the Ivy modules (which is not the standard)
* Tracking Issue: [GRADLE-446](https://bugs.indeed.com/browse/GRADLE-446)
* Commits: [9218271](https://code.corp.indeed.com/gradle/gradle/commit/9218271a8464f74d901b889f362c391bb3326073), [7fa7ac5](https://code.corp.indeed.com/gradle/gradle/commit/7fa7ac50d800d9d6d61f5d3c51a9120d286370cd)
* Cause: We publish ivy artifacts with a non-null `classifier` attribute and `-src` appended to the `name` attribute, which not what Gradle expects by default.
* Summary: Disable `resolveSourceArtifacts` in the local repository. Configure the default ivy artifact name to match Indeed conventions.

### "Fixes" the configuration requested for certain dependencies to assist in Ivy->Maven migration
* Tracking Issue: [GRADLE-442](https://bugs.indeed.com/browse/GRADLE-442)
* Commits:
[1cb7b00](https://code.corp.indeed.com/gradle/gradle/commit/1cb7b00ed8f48060c75c8ef173d16e04ae48a4f5)
[d4b948a](https://code.corp.indeed.com/gradle/gradle/commit/d4b948a217b218780b50f16e6f6fd92002953be1)
[575e0af](https://code.corp.indeed.com/gradle/gradle/commit/575e0af012e12fc37859da491661bba5cebdf751)
[e625008](https://code.corp.indeed.com/gradle/gradle/commit/e625008f2e666c860850b28f4f4dec8b84234163)
[9218271](https://code.corp.indeed.com/gradle/gradle/commit/9218271a8464f74d901b889f362c391bb3326073)
[4554f5e](https://code.corp.indeed.com/gradle/gradle/commit/4554f5e2e937aa104977f4574317861b9b94f464)
* Cause: Gradle does [configuration mapping](https://ant.apache.org/ivy/history/2.5.0/ivyfile/configurations.html) for Ivy using the required configuration name. Some Indeed-specific configurations don't exist in the Maven and Gradle world. When we migrate the project from Ant to Gradle, the published artifact is changed from Ivy format to Maven/Gradle format, with the result that the required configuration doesn't exist in the latest artifact. So we need to redirect them to enable Gradle to find the correct configuration. Otherwise we have to fix hundreds of projects and republish all depended libraries.
* Summary:
    * IF: 
        * A request is made TO a **Maven** or **Ivy** module and the request is asking for one of these configurations:
            * `closure`
            * `common`
            * `compileinterfaces`
            * `compile-interfaces`
            * `interfaces`
            * `master`
            * `messagebundle`
            * `po`
            * `proto`
            * `source`
            * `sources`
            * `soy`
            * `testbase`
            * `tests`
            * `withPo`
            * `withWeb`
        * and that configuration does not exist in the target module
    * THEN:
        * The request is modified to target the `default` configuration instead.
    * ELSE IF:
        * the request is made FROM an **Ivy** module TO a **Maven** module
        * and the request is asking for one of these configurations: `master`, `compile`, `provided`, `runtime`, `test`, `sources`
    * THEN: 
        * The request is modified to target `default` instead
    * ELSE IF:
        * the request is made FROM a **Non-Ivy** module TO an **Ivy** module
        * and the `interfaces` configuration exists in the target **Ivy** module
        * and that `interfaces` configuration contains at least one artifact
    * THEN: 
        * The request is modified to target `interfaces` instead
    * ELSE IF:
        * the request is made FROM a **Maven** module TO an **Ivy** module
    * THEN: 
        * The request is modified to target `default` always

### Disabled "compatible version range resolution" (introduced in Gradle 4.3)
* Tracking Issue: [GRADLE-442](https://bugs.indeed.com/browse/GRADLE-442)
* Commits: 
[fbf02ac](https://code.corp.indeed.com/gradle/gradle/commit/fbf02ac1737a3cba4d664db94118be91b1c6267e)
[47d6083](https://code.corp.indeed.com/gradle/gradle/commit/47d60835c71a81812a0f858fbdc5f4d9fe5f252d)
[378e537](https://code.corp.indeed.com/gradle/gradle/commit/378e53788dfa3184e0ac80e7b48f042ce43a80f7)
* Cause: At Indeed, we want to resolve the latest version with the major version equal to the Indeed Universal Version. Before OSS Gradle 4.3, we could use `RangedVersionSelector` to accomplish this. However, Gradle 4.3 changed the definition which broke our usage, because we can't specify "the newest patch of major version 4". If we don't disable the "compatible version range", the conflict resolution between `3.1.5` and `[0, 5)` will result in `3.1.5` instead of the desired result of "newest version with major version 4."
* Summary: Disable "Compatible Version Range Resolution" since the Indeed artifact rewriter in `common-gradle-plugin` sets the version to `[0, ${indeed.universe.version} + 1)` if the version isn't specified for an Indeed dependency. The expected version we want to get from it is the `latest` version within this range.

### Scripts used to build Gradle locally and on Jenkins, and to publish Gradle to Nexus
* Tracking Issue: [GRADLE-254](https://bugs.indeed.com/browse/GRADLE-254)
* Commits:
[f94df8a](https://code.corp.indeed.com/gradle/gradle/commit/f94df8aa268add00d0961ef855b9414299337236)
[50afaaa](https://code.corp.indeed.com/gradle/gradle/commit/50afaaaa780074745c102058f0802b4e99f76ef3)
[2b8d7db](https://code.corp.indeed.com/gradle/gradle/commit/2b8d7db0f3c787660b3ccad4a02d2abf1653252a)
[b97d30a](https://code.corp.indeed.com/gradle/gradle/commit/b97d30a6506053ca68462359c49b5103c18261eb)
[98b2158](https://code.corp.indeed.com/gradle/gradle/commit/98b215850bf0badf28a5133ac468c3758ef38a96)
[cfa740f](https://code.corp.indeed.com/gradle/gradle/commit/cfa740fb427002527b0ca8cba92d01d181cee0b0)
[378e537](https://code.corp.indeed.com/gradle/gradle/commit/378e53788dfa3184e0ac80e7b48f042ce43a80f7)
[0ff677f](https://code.corp.indeed.com/gradle/gradle/commit/0ff677f21e53cfe078d63ac6b82b455afc9837a7)
[424b723](https://code.corp.indeed.com/gradle/gradle/commit/424b723b4522c511daf48ec37ffcbd620f365e6f)
[ced311b](https://code.corp.indeed.com/gradle/gradle/commit/ced311b5fdbbe937a20593764b76072aecfe9aa7)
[8529728](https://code.corp.indeed.com/gradle/gradle/commit/852972840858e6a2810f365d47939d5a62531e6a)
[360bf40](https://code.corp.indeed.com/gradle/gradle/commit/360bf4013f4648fa9f47de1bc184ecc728096b30)
[09312dc](https://code.corp.indeed.com/gradle/gradle/commit/09312dcc8477fe3fed8ae8866f24565feb62ed1f)
* Cause: We need a way to build and publish the Gradle Core to Nexus.
* Summary: Add `deploy.jenkinsfile`, `indeed-build-dev.sh`, `indeed-build-jenkins.sh`, `indeed-build-prod.sh`, `indeed-publish.sh` file.

### Detection of circular dependencies
* Tracking Issue: [GRADLE-440](https://bugs.indeed.com/browse/GRADLE-440)
* Commits: [661b9ba](https://code.corp.indeed.com/gradle/gradle/commit/661b9ba2216a93996c988f0f9c3347353d28d872)
* Cause: We will convert every dependency specified in the `pom.xml` file and every dependency with a `force=true` attribute in the `ivy.xml` file into an OverrideRule(Indeed specific hack),
when we combine all OverrideRule together, there might be some loops in those OverrideRules. We need to detect them and abort.
* Summary: Detect the loop in all OverrideRules, and abort with reason if the loop is detected.

### Support for one-to-none and one-to-more dependency substitution(rewrite).
* Tracking Issue: [GRADLE-438](https://bugs.indeed.com/browse/GRADLE-438)
* Commits: 
[6017a17](https://code.corp.indeed.com/gradle/gradle/commit/6017a172067cd37b671951351823bd657d4f9745)
[c21123d](https://code.corp.indeed.com/gradle/gradle/commit/c21123d3a4fe2a029bced4ccb760e54ceed8fc01)
[466258e](https://code.corp.indeed.com/gradle/gradle/commit/466258ec4adad19759f5e963cdf6bf3e36db6c6a)
[1db285a](https://code.corp.indeed.com/gradle/gradle/commit/1db285a0c83d12ae0513aeb365b5cd7b7e7bdd52)
[b4f8de9](https://code.corp.indeed.com/gradle/gradle/commit/b4f8de914441fff9da9e6302e278ec02af50fd4f)
[0e91c42](https://code.corp.indeed.com/gradle/gradle/commit/0e91c425fe240d0f2c3f8c5a82588e2b0e0042ea)
* Cause: Gradle Dependency Substitution only supports one-to-one substitution. 
We would like to have the ability to do one-to-none (ignore the dependency) and one-to-more substitution.
We can define rules at [`javadev/gradle/resolve-rules.d`](https://code.corp.indeed.com/delivery/javadev/blob/master/gradle/resolve-rules.d)
to do dependency substitution globally for all Indeed projects.
* Summary: [`common-gradle-plugin`](https://code.corp.indeed.com/gradle/common-gradle-plugin) parses the rule defined at [`javadev/gradle/resolve-rules.d`](https://code.corp.indeed.com/delivery/javadev/blob/master/gradle/resolve-rules.d),
and passes the rule in the format of `<group>:<name>:\{\{(<rewrite_group>:<rewrite_name>:[<rewrite_version>]](||<rewrite_group>:<rewrite_name>:[<rewrite_version>])*)?` to the Gradle Core.
During the resolution, Gradle Core will:
    * Ignore the module(`${group}:${name}`) if the module identifier is equal to `${group}:${name}:{{`
    * Ignore the module(`${group}:${name})` and add lists of rewrite modules into the dependency resolution if the module identifier is equal to `${group}:${name}:{{<rewritten_group1>:<rewritten_name1>:<rewritten_version1>||<rewritten_group2>:<rewritten_name2>:<rewritten_version2>||...` 

### Sort the participating modules in VirtualPlatformState
* Tracking Issue: [GRADLE-284](https://bugs.indeed.com/browse/GRADLE-284)
* Commits:
[7722ae7](https://code.corp.indeed.com/gradle/gradle/commit/7722ae79faa15cbdad976ac84e1e3600829f9262)
[0d4adc8](https://code.corp.indeed.com/gradle/gradle/commit/0d4adc82122253bb6b4ca966291bfce3e3d7adc3)
[4d44f7f](https://code.corp.indeed.com/gradle/gradle/commit/4d44f7f7ad3fcacccdfcb82ae788709d6cb39728)
* Cause: [gradle/gradle#11714](https://github.com/gradle/gradle/issues/11714) causes ModuleResolveState to be inserted into this set in a non-deterministic order between builds.
The result is unnecessary compilations, as the order of participating modules affects the order of edge creation, which affects node creation, 
which affects the order of the classpath, which is used to determine if compilation is up-to-date.
* Summary: Switch the container of participating modules from HashSet to TreeSet to ensure deterministic ordering of participating modules.

### Support for busting repository cache by adding a special token to the repository
* Tracking Issue: [GRADLE-435](https://bugs.indeed.com/browse/GRADLE-435)
* Commits:
[19368ce](https://code.corp.indeed.com/gradle/gradle/commit/19368ce0b6097080aa6cadfc59771cdd8ac3d77c)
[8d84547](https://code.corp.indeed.com/gradle/gradle/commit/8d845476391834fd64dc26ad66fe81f37ff1d9a0)
* Cause: Once Gradle caches the existence of a component(`module:version`), it never forgets or rechecks it.
In the following cases, we need a way to bust all Gradle caches for a dependency:

    * We move a dependency from the Ivy repo to the Maven repo without publishing a new version
    * We have to patch an existing published version of a dependency (unusual, but has also happened)

    If we bust the repo cache by changing that file in javadev, then the Gradle used by every developer at Indeed will recheck all dependencies.

* Summary: By increasing the value in [`delivery/javadev gradle/cacheBuster.txt`](https://code.corp.indeed.com/delivery/javadev/blob/master/gradle/cacheBuster.txt),
we can bust the cache for the Ivy and Maven repo, causing Gradle to recheck the dependency.
