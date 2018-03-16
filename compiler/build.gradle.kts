
import java.io.File
import org.gradle.api.tasks.bundling.Jar
import org.jetbrains.kotlin.gradle.dsl.KotlinCompile

plugins {
    kotlin("jvm")
    id("jps-compatible")
}

jvmTarget = "1.6"

val compilerModules: Array<String> by rootProject.extra
val otherCompilerModules = compilerModules.filter { it != path }

val effectSystemEnabled: Boolean by rootProject.extra
val newInferenceEnabled: Boolean by rootProject.extra

configureFreeCompilerArg(effectSystemEnabled, "-Xeffect-system")
configureFreeCompilerArg(newInferenceEnabled, "-Xnew-inference")

fun configureFreeCompilerArg(isEnabled: Boolean, compilerArgument: String) {
    if (isEnabled) {
        allprojects {
            tasks.withType<KotlinCompile<*>> {
                kotlinOptions {
                    freeCompilerArgs += listOf(compilerArgument)
                }
            }
        }
    }
}

val depDistProjects = listOf(
        ":kotlin-script-runtime",
        ":kotlin-stdlib",
        ":kotlin-test:kotlin-test-jvm"
)
val antLauncherJar by configurations.creating

dependencies {
    testRuntime(intellijDep()) // Should come before compiler, because of "progarded" stuff needed for tests

    depDistProjects.forEach {
        testCompile(project(it))
    }
    testCompile(commonDep("junit:junit"))
    testCompileOnly(project(":kotlin-test:kotlin-test-jvm"))
    testCompileOnly(project(":kotlin-test:kotlin-test-junit"))
    testCompile(projectTests(":compiler:tests-common"))
    testCompile(projectTests(":generators:test-generator"))
    testCompile(project(":compiler:ir.ir2cfg"))
    testCompile(project(":compiler:ir.tree")) // used for deepCopyWithSymbols call that is removed by proguard from the compiler TODO: make it more straightforward
    testCompile(project(":kotlin-scripting-compiler"))
    testCompile(project(":kotlin-script-util"))
    testCompileOnly(projectRuntimeJar(":kotlin-daemon-client"))
    testCompileOnly(project(":kotlin-reflect-api"))
    testCompile(commonDep("org.jetbrains.kotlinx", "kotlinx-coroutines-jdk8")) { isTransitive = false }
    testCompile(commonDep("io.ktor", "ktor-network")) {
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-reflect")
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib")
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-jdk8")
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-jdk7")
    }
    otherCompilerModules.forEach {
        testCompileOnly(project(it))
    }
    testCompileOnly(intellijCoreDep()) { includeJars("intellij-core") }
    testCompileOnly(intellijDep()) { includeJars("openapi", "idea", "idea_rt", "util", "asm-all", rootProject = rootProject) }

    testRuntime(project(":kotlin-reflect"))
    testRuntime(project(":kotlin-daemon-client"))
    testRuntime(project(":compiler:daemon")) // +
    testRuntime(project(":compiler:daemon-common")) // +
    testRuntime(commonDep("org.jetbrains.kotlinx", "kotlinx-coroutines-jdk8")) { isTransitive = false }
    testRuntime(commonDep("io.ktor", "ktor-network")) {
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-reflect")
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib")
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-jdk8")
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-jdk7")
    }
    testRuntime(androidDxJar())
    testRuntime(files(toolsJar()))

    antLauncherJar(commonDep("org.apache.ant", "ant"))
    antLauncherJar(files(toolsJar()))
}

sourceSets {
    "main" {}
    "test" {
        projectDefault()
    }
}

val jar: Jar by tasks
jar.from("../idea/src") {
    include("META-INF/extensions/compiler.xml")
}

projectTest {
    dependsOn(*testDistProjects.map { "$it:dist" }.toTypedArray())
    workingDir = rootDir
    systemProperty("kotlin.test.script.classpath", testSourceSet.output.classesDirs.joinToString(File.pathSeparator))
    doFirst {
        systemProperty("kotlin.ant.classpath", antLauncherJar.asPath)
        systemProperty("kotlin.ant.launcher.class", "org.apache.tools.ant.Main")
    }
}


val generateTests by generator("org.jetbrains.kotlin.generators.tests.GenerateCompilerTestsKt")

testsJar()
