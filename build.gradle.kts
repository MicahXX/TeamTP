plugins {
    id("java-library")
    id("xyz.jpenilla.run-paper") version "3.1.0"
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

val paperApiVersion = providers.gradleProperty("paperApiVersion").get()
val minecraftVersion = providers.gradleProperty("minecraftVersion").get()
val pluginApiVersion = providers.gradleProperty("pluginApiVersion").get()
val targetJavaVersion = providers.gradleProperty("javaVersion").get().toInt()

dependencies {
    compileOnly("io.papermc.paper:paper-api:$paperApiVersion")
    compileOnly("net.luckperms:api:5.4")
    testImplementation("io.papermc.paper:paper-api:$paperApiVersion")
    testImplementation("net.luckperms:api:5.4")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testImplementation("org.mockito:mockito-core:5.20.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(targetJavaVersion)
}

val serverJavaLauncher = extensions.getByType<JavaToolchainService>().launcherFor {
    languageVersion = JavaLanguageVersion.of(targetJavaVersion)
}

tasks {
    test {
        useJUnitPlatform()
    }
    runServer {
        minecraftVersion(minecraftVersion)
        javaLauncher = serverJavaLauncher
        jvmArgs("-Xms2G", "-Xmx2G")
    }

    processResources {
        val props = mapOf("version" to version, "apiVersion" to pluginApiVersion)
        inputs.properties(props)
        filesMatching("plugin.yml") {
            expand(props)
        }
    }
}
