// Плагин foojay-resolver сам скачает нужный JDK для toolchain, если его нет локально
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "agent-contour"
