/// Цикл агента — слайд 5. Написан руками, чтобы было видно, где что происходит.
///
/// Фреймворк умеет то же самое одной строкой (AiServices.builder(...).tools(...)),
/// и в бою так и делают. Но пока цикл спрятан внутри, непонятно, куда вставлять
/// свой код. Здесь он на виду, и четыре точки вставки (слайд 6) обозначены цифрами.
package agentcontour;

import agentcontour.contour.Contour;
import agentcontour.tools.CourseTools;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import java.util.ArrayList;
import java.util.List;

public final class Agent {

    private final ChatModel model;
    private final CourseTools tools;
    private final Contour contour;

    // Разговор, который помнит агент. Память ведём мы, а не модель:
    // она каждый раз видит только то, что мы ей покажем
    private final List<ChatMessage> history = new ArrayList<>();

    public Agent(ChatModel model, CourseTools tools, Contour contour) {
        this.model = model;
        this.tools = tools;
        this.contour = contour;
    }

    public String run(String request) {
        // [1] До вызова модели: собираем контекст. Инструкция, факты от владельца,
        //     хвост разговора — всё, что модель увидит. Больше она не знает ничего
        List<ChatMessage> messages = contour.before().build(history, request);

        for (int round = 1; ; round++) {
            ChatRequest chatRequest = ChatRequest.builder()
                    .messages(messages)
                    .toolSpecifications(tools.specifications())
                    .build();

            // [2] Вокруг вызова модели: срок, предел кругов, запись в журнал.
            //     Если срок вышел или кругов слишком много — приходит готовый
            //     ответ без вызовов инструментов, и цикл заканчивается штатно
            AiMessage reply = contour.aroundModel().call(model, chatRequest, round);

            messages.add(reply);

            // Нужен инструмент? Нет — отдаём ответ человеку
            if (!reply.hasToolExecutionRequests()) {
                // [4] После ответа: тема, формат, секреты. Только потом наружу
                String answer = contour.after().check(reply.text());

                history.add(UserMessage.from(request));
                history.add(AiMessage.from(answer));

                return answer;
            }

            // Да — вызываем. Модель только ПРЕДЛОЖИЛА вызов; решает код
            for (ToolExecutionRequest call : reply.toolExecutionRequests()) {
                // [3] Вокруг вызова инструмента: права, подтверждение необратимого,
                //     журнал. Результат уходит обратно в модель, и круг повторяется
                String result = contour.aroundTool().invoke(tools, call);

                messages.add(ToolExecutionResultMessage.from(call, result));
            }
        }
    }
}
