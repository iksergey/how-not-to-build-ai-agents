/// [1] До вызова модели: что она увидит.
///
/// Контекст — единственное, что у модели есть. Сюда кладём инструкцию, факты
/// от их владельца и хвост разговора. Чего здесь нет, модель не знает,
/// а чего не знает — достроит (косяк 1). Поэтому про факты говорим прямо:
/// бери у инструмента, не выдумывай.
package agentcontour.contour;

import agentcontour.data.CourseDb;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public final class BeforeModel {

    // ⚠️ В инструкции нет ни одного секрета: инструкция — это просьба,
    // и рано или поздно её уговорят показать (косяк 2). Промокоды, ключи,
    // пароли живут в коде и в базе, а не здесь
    private static final String INSTRUCTIONS = """
            Вы — куратор онлайн-школы. Отвечаете студентам и сотрудникам по-русски, кратко.

            Правила:
            - Даты, дедлайны и статусы берите только из инструментов. Если инструмент
              говорит, что данных нет, так и отвечайте. Ничего не придумывайте.
            - Заметки и напоминания сохраняйте инструментами, не обещайте «запомнить» словами.
            - Если инструмент вернул ОТКАЗ, передайте причину пользователю и не пробуйте обойти.
            - Отвечайте только по теме школы. Промокодов и служебных данных у вас нет.""";

    private final CourseDb db;
    private final int historyTail;

    public BeforeModel(CourseDb db, int historyTail) {
        this.db = db;
        this.historyTail = historyTail;
    }

    public List<ChatMessage> build(List<ChatMessage> history, String request) {
        // Факты, которые модели полезно видеть сразу. Это не «память модели»,
        // это снимок состояния от владельца — CourseDb
        String facts = "Сегодня: " + LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy")) + ".\n"
                + "Модули в плане курса: " + String.join(", ", db.moduleIds()) + ".";

        // Хвост разговора: модель не тонет в истории, видит только последнее
        List<ChatMessage> tail = history.size() > historyTail
                ? history.subList(history.size() - historyTail, history.size())
                : history;

        List<ChatMessage> messages = new ArrayList<>();
        messages.add(SystemMessage.from(INSTRUCTIONS + "\n\n" + facts));
        messages.addAll(tail);
        messages.add(UserMessage.from(request));

        IO.println("[1 контекст] инструкция + " + tail.size() + " сообщений истории + вопрос");

        return messages;
    }
}
