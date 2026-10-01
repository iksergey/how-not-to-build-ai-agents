/// [2] Вокруг вызова модели: срок, предел кругов, журнал.
///
/// У всего есть конец (косяк 6). Модель может молчать — тогда срок. Модель
/// может бесконечно звать инструмент — тогда предел кругов. В обоих случаях
/// цикл получает обычный ответ без вызовов и заканчивается штатно, а в
/// журнале остаётся запись, почему.
package agentcontour.contour;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import java.time.Duration;
import java.util.List;
import java.util.Map;

public final class AroundModel {

    private final Journal journal;
    private final Duration timeout;
    private final int maxRounds;

    public AroundModel(Journal journal, Duration timeout, int maxRounds) {
        this.journal = journal;
        this.timeout = timeout;
        this.maxRounds = maxRounds;
    }

    public AiMessage call(ChatModel model, ChatRequest request, int round) {
        if (round > maxRounds) {
            IO.println("[2 модель] круг " + round + ": предел " + maxRounds + " исчерпан, останавливаем");
            journal.write("model.stop", Map.of("round", round, "reason", "предел кругов"));

            return AiMessage.from("Не удалось закончить за " + maxRounds + " шагов. Задача не выполнена, "
                    + "это записано в журнал. Попробуйте позже или уточните запрос.");
        }

        IO.println("[2 модель] круг " + round + ": вызов, срок " + timeout.toSeconds() + " с");
        long started = System.nanoTime();

        ChatResponse response;
        try {
            // Срок задан в самом клиенте (OllamaChatModel.timeout): по его истечении
            // прилетает исключение, и мы превращаем его в записанный итог
            response = model.chat(request);
        } catch (RuntimeException error) {
            // Тишина — не «готово», это записанный итог
            IO.println("[2 модель] модель не ответила: " + Contour.shortText(error.getMessage(), 100));
            journal.write("model.timeout", Map.of("round", round, "seconds", timeout.toSeconds(),
                    "error", String.valueOf(error.getMessage())));

            return AiMessage.from("Модель не ответила вовремя. Запрос не выполнен, это записано в журнал.");
        }

        AiMessage reply = response.aiMessage();

        List<String> calls = reply.hasToolExecutionRequests()
                ? reply.toolExecutionRequests().stream().map(ToolExecutionRequest::name).toList()
                : List.of();

        TokenUsage usage = response.tokenUsage();

        journal.write("model.call", Map.of(
                "round", round,
                "ms", (System.nanoTime() - started) / 1_000_000,
                "tokensIn", usage == null || usage.inputTokenCount() == null ? 0 : usage.inputTokenCount(),
                "tokensOut", usage == null || usage.outputTokenCount() == null ? 0 : usage.outputTokenCount(),
                "toolCalls", calls,
                "text", Contour.shortText(reply.text(), 160)));

        IO.println(calls.isEmpty()
                ? "[2 модель] ответ текстом"
                : "[2 модель] просит инструмент: " + String.join(", ", calls));

        return reply;
    }
}
