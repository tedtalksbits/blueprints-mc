// Development-only plugin: verifies the palette, transforms, and exact placement against a real Paper server.
// Never install it on a live server; it writes test blocks near world spawn at high Y.
plugins { java }

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.2.build.130-stable")
    compileOnly(project(":server-plugin"))
}

java { toolchain.languageVersion.set(JavaLanguageVersion.of(25)) }
