/// [3] Вокруг вызова инструмента: права, подтверждение, журнал.
///
/// Модель только предложила вызов. Здесь решает код:
///   - есть ли такой инструмент вообще (права — это список, который дали модели);
///   - необратимое подтверждает человек, а не модель;
///   - каждый вызов с аргументами и результатом ложится в журнал. Это квитанция:
///     сделано только то, что здесь записано (косяк 4).
///
/// Границы самих действий (путь внутри рабочей папки, формат кода модуля)
/// проверяют инструменты: граница живёт в инструменте (косяк 3), а не в
/// инструкции модели.
package agentcontour.contour;

import agentcontour.tools.CourseTools;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.service.tool.ToolExecutor;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;

public final class AroundTool {

    // Инструменты, после которых не отмотать назад. Их список — решение
    // архитектора, модель на него не влияет
    private static final Set<String> IRREVERSIBLE = Set.of("publishModule");

    private final Journal journal;
    private final BiPredicate<String, String> approve;

    public AroundTool(Journal journal, BiPredicate<String, String> approve) {
        this.journal = journal;
        this.approve = approve;
    }

    public String invoke(CourseTools tools, ToolExecutionRequest call) {
        String args = call.arguments() == null ? "{}" : call.arguments();

        ToolExecutor executor = tools.executor(call.name());

        if (executor == null) {
            // Модель попросила то, чего ей не давали. Не падаем: отвечаем
            // текстом, модель передаст отказ пользователю
            return done(call.name(), args, "ОТКАЗ: такого инструмента нет", "no-such-tool");
        }

        if (IRREVERSIBLE.contains(call.name()) && !approve.test(call.name(), args)) {
            return done(call.name(), args, "ОТКАЗ: человек не подтвердил действие", "declined");
        }

        try {
            return done(call.name(), args, executor.execute(call, "default"), "ok");
        } catch (RuntimeException error) {
            // Ошибка инструмента — тоже результат, и тоже в журнал.
            // Молча проглотить её — значит получить «сделано», которого не было
            return done(call.name(), args, "ОШИБКА: " + error.getMessage(), "error");
        }
    }

    private String done(String name, String args, String result, String status) {
        IO.println("[3 инструмент] " + name + "(" + args + ") → " + Contour.shortText(result, 120));

        journal.write("tool.call", Map.of("tool", name, "args", args, "status", status, "result", result));

        return result;
    }

    /// Подтверждение человеком. Без терминала (ввод перенаправлен) отвечаем «нет»:
    /// отказ дешевле, чем необратимое действие без спроса
    public static boolean askHuman(String tool, String args) {
        IO.println("[подтверждение] агент хочет вызвать " + tool + " с " + args);

        if (System.console() == null) {
            IO.println("[подтверждение] терминала нет — отказ по умолчанию");
            return false;
        }

        String answer = IO.readln("[подтверждение] Разрешить? Y/N: ");
        return answer != null && (answer.strip().equalsIgnoreCase("y") || answer.strip().equalsIgnoreCase("д"));
    }
}
