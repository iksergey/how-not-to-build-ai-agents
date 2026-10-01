// Свой агент: цикл и четыре точки вставки. Код к докладу «Как не надо создавать ИИ-агентов».
//
// Запуск:  ./gradlew run                      — восемь сценариев, по одному на косяк
//          ./gradlew run --args="--chat"      — разговор с агентом руками

plugins {
    java
    application
}

java { toolchain { languageVersion = JavaLanguageVersion.of(26) } }

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    // Без этого флага имена параметров пропадают из байткода, и модель видит
    // аргументы инструментов как arg0, arg1 вместо moduleId, text
    options.compilerArgs.add("-parameters")
}

repositories { mavenCentral() }

// Те же библиотеки и версии, что в курсе: LangChain4j с клиентом Ollama
val langchain4j = "1.20.0"

dependencies {
    implementation("dev.langchain4j:langchain4j:$langchain4j")
    implementation("dev.langchain4j:langchain4j-ollama:$langchain4j")
    // Журнал и состояние пишем в JSON
    implementation("com.fasterxml.jackson.core:jackson-databind:2.22.2")
    // Заглушка логгера: LangChain4j пишет через SLF4J, без провайдера тот сыплет предупреждениями
    runtimeOnly("org.slf4j:slf4j-nop:2.0.19")
}

application { mainClass = "agentcontour.Main" }

tasks.named<JavaExec>("run") {
    standardInput = System.`in`   // подтверждение необратимого действия читается с клавиатуры
    workingDir = projectDir       // рабочая папка агента создаётся рядом с исходниками
}
