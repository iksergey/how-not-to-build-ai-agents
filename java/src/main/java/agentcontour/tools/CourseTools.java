/// Инструменты — руки агента. Отдавая инструмент модели, вы отдаёте ей свои
/// права целиком, поэтому границы стоят внутри инструмента, а не в инструкции.
///
/// Описания (@Tool и @P) читает модель: по ним она решает, что и когда звать.
/// Тексты результатов читает тоже модель, поэтому пустых ответов нет: «не найдено»
/// говорим словами, иначе модель заполнит пустоту сама.
package agentcontour.tools;

import agentcontour.data.CourseDb;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import dev.langchain4j.service.tool.DefaultToolExecutor;
import dev.langchain4j.service.tool.ToolExecutor;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CourseTools {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final CourseDb db;
    private final Path workspace;
    private int buildChecks;

    // Описания для модели и исполнители для кода собираются из одних и тех же
    // методов с @Tool: описание — что модель видит, исполнитель — что код делает
    private final List<ToolSpecification> specifications;
    private final Map<String, ToolExecutor> executors = new LinkedHashMap<>();

    public CourseTools(CourseDb db, Path workspace) {
        this.db = db;
        this.workspace = workspace;
        this.specifications = ToolSpecifications.toolSpecificationsFrom(this);

        for (Method method : CourseTools.class.getDeclaredMethods()) {
            if (method.isAnnotationPresent(Tool.class)) {
                executors.put(ToolSpecifications.toolSpecificationFrom(method).name(),
                        new DefaultToolExecutor(this, method));
            }
        }
    }

    public List<ToolSpecification> specifications() {
        return specifications;
    }

    public ToolExecutor executor(String name) {
        return executors.get(name);
    }

    @Tool("Возвращает дедлайн модуля курса по его коду. Единственный источник дат.")
    public String getModuleDeadline(
            @P("Код модуля: буква M, дефис и две цифры, например M-02") String moduleId) {
        // Косяк 1: «не знаю» говорит код. Модель получает это текстом и передаёт дальше
        return db.deadline(moduleId)
                .map(date -> "Дедлайн модуля " + moduleId.toUpperCase() + ": " + date.format(DATE) + ".")
                .orElse("Модуля " + moduleId + " нет в плане курса. Дедлайна у него нет.");
    }

    @Tool("Сохраняет текстовую заметку в файл в рабочей папке агента.")
    public String saveNote(
            @P("Имя файла, например заметки/дедлайны.txt") String fileName,
            @P("Текст заметки") String text) {
        // Косяк 3: граница в инструменте. Путь сверяется с рабочей папкой ДО записи.
        // «../../../важное.txt» после нормализации окажется выше — отказ
        Path full = workspace.resolve(fileName).toAbsolutePath().normalize();

        if (!full.startsWith(workspace)) {
            return "ОТКАЗ: путь «" + fileName + "» ведёт за пределы рабочей папки. "
                    + "Записывать можно только внутрь неё.";
        }

        try {
            Files.createDirectories(full.getParent());
            Files.writeString(full, text);
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }

        return "Заметка сохранена: " + workspace.relativize(full) + ".";
    }

    @Tool("Записывает напоминание в список дел куратора. Вызывать, когда просят что-то запомнить.")
    public String addReminder(@P("Текст напоминания") String text) {
        // Косяк 4 и 5: запись идёт через единственный путь владельца состояния.
        // Результат проверяем не по словам агента, а по state.json и журналу
        db.addReminder(text);

        return "Напоминание записано. Всего напоминаний: " + db.reminders().size() + ".";
    }

    @Tool("Возвращает служебную справку о льготах для сотрудников школы.")
    public String getStaffBenefits() {
        // Данные приносят секрет с собой: так утечки и случаются в жизни. Не модель
        // его выдумала, он лежал в справке. Ловит проверка на выходе (AfterAnswer)
        return "Сотрудникам: бесплатный доступ ко всем курсам, скидка близким по промокоду SHKOLA-50, "
                + "отпуск на обучение 5 дней в год.";
    }

    @Tool("Возвращает состояние сборки задачи по её номеру, например build-42.")
    public String getBuildStatus(@P("Номер задачи") String buildId) {
        // Косяк 6: инструмент нарочно никогда не заканчивает. Если модель будет
        // звать его по кругу, остановит предел кругов в AroundModel, а не удача
        buildChecks++;

        return "Сборка " + buildId + " ещё выполняется (проверка №" + buildChecks + "). "
                + "Результата пока нет, вызовите getBuildStatus снова.";
    }

    @Tool("Открывает модуль курса всем студентам. Действие необратимо.")
    public String publishModule(
            @P("Код модуля: буква M, дефис и две цифры, например M-02") String moduleId) {
        // Сюда код попадает только после подтверждения человеком (AroundTool)
        return db.deadline(moduleId)
                .map(date -> "Модуль " + moduleId.toUpperCase() + " опубликован для всех студентов.")
                .orElse("ОТКАЗ: модуля " + moduleId + " нет в плане курса, публиковать нечего.");
    }
}
