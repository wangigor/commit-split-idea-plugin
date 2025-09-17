plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.9.10"
    id("org.jetbrains.intellij") version "1.16.1"
}

group = "com.github.commitSplitter"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.eclipse.jgit:org.eclipse.jgit:6.7.0.202309050840-r")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.15.2")
    
    // Add SLF4J for JGit logging
    implementation("org.slf4j:slf4j-api:2.0.9")
    implementation("org.slf4j:slf4j-simple:2.0.9")
}

intellij {
    version.set("2021.1")
    type.set("IC") // IntelliJ IDEA Community Edition
    
    plugins.set(listOf("Git4Idea"))
    
    // 禁用插桩来解决问题
    instrumentCode.set(false)
}

tasks {
    withType<JavaCompile> {
        sourceCompatibility = "11"
        targetCompatibility = "11"
    }
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions.jvmTarget = "11"
    }
}