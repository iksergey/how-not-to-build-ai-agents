"""Цикл агента — слайд 5. Написан руками, чтобы было видно, где что происходит.

Фреймворк умеет то же самое одной строкой (client.as_agent(...)), и в бою так
и делают. Но пока цикл спрятан внутри, непонятно, куда вставлять свой код.
Здесь он на виду, и четыре точки вставки (слайд 6) обозначены цифрами.
"""

from agent_framework import ChatOptions, Content, FunctionTool, Message

from agent_contour.contour import Contour


class Agent:
    def __init__(self, model, tools: list[FunctionTool], contour: Contour) -> None:
        self.model = model
        self.tools = tools
        self.contour = contour
        # Разговор, который помнит агент. Память ведём мы, а не модель:
        # она каждый раз видит только то, что мы ей покажем
        self.history: list[Message] = []

    async def run(self, request: str) -> str:
        # [1] До вызова модели: собираем контекст. Инструкция, факты от владельца,
        #     хвост разговора — всё, что модель увидит. Больше она не знает ничего
        messages = self.contour.before.build(self.history, request)

        options = ChatOptions(tools=self.tools, tool_choice="auto")

        round_no = 0
        while True:
            round_no += 1

            # [2] Вокруг вызова модели: срок, предел кругов, запись в журнал.
            #     Если срок вышел или кругов слишком много — приходит готовый
            #     ответ без вызовов инструментов, и цикл заканчивается штатно
            response = await self.contour.around_model.call(self.model, messages, options, round_no)

            messages.extend(response.messages)

            calls = [
                content
                for message in response.messages
                for content in message.contents
                if content.type == "function_call"
            ]

            # Нужен инструмент? Нет — отдаём ответ человеку
            if not calls:
                # [4] После ответа: тема, формат, секреты. Только потом наружу
                answer = self.contour.after.check(response.text)

                self.history.append(Message("user", [Content.from_text(request)]))
                self.history.append(Message("assistant", [Content.from_text(answer)]))

                return answer

            # Да — вызываем. Модель только ПРЕДЛОЖИЛА вызов; решает код
            for call in calls:
                # [3] Вокруг вызова инструмента: права, подтверждение необратимого,
                #     журнал. Результат уходит обратно в модель, и круг повторяется
                result = await self.contour.around_tool.invoke(self.tools, call)

                messages.append(
                    Message("tool", [Content.from_function_result(call.call_id, result=result)])
                )
