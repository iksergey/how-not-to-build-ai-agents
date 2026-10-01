"""[2] Вокруг вызова модели: срок, предел кругов, журнал.

У всего есть конец (косяк 6). Модель может молчать — тогда срок. Модель
может бесконечно звать инструмент — тогда предел кругов. В обоих случаях
цикл получает обычный ответ без вызовов и заканчивается штатно, а в
журнале остаётся запись, почему.
"""

import asyncio
import time

from agent_framework import ChatOptions, ChatResponse, Content, Message

from agent_contour.contour.journal import Journal


class AroundModel:
    def __init__(self, journal: Journal, timeout: float, max_rounds: int) -> None:
        self.journal = journal
        self.timeout = timeout
        self.max_rounds = max_rounds

    async def call(self, model, messages: list[Message], options: ChatOptions, round_no: int) -> ChatResponse:
        if round_no > self.max_rounds:
            print(f"[2 модель] круг {round_no}: предел {self.max_rounds} исчерпан, останавливаем")
            self.journal.write("model.stop", {"round": round_no, "reason": "предел кругов"})

            return _final(
                f"Не удалось закончить за {self.max_rounds} шагов. Задача не выполнена, "
                "это записано в журнал. Попробуйте позже или уточните запрос."
            )

        print(f"[2 модель] круг {round_no}: вызов, срок {self.timeout:.0f} с")
        started = time.perf_counter()

        try:
            response = await asyncio.wait_for(
                model.get_response(messages, options=options), timeout=self.timeout
            )
        except TimeoutError:
            # Срок вышел. Тишина — не «готово», это записанный итог
            print(f"[2 модель] срок {self.timeout:.0f} с вышел")
            self.journal.write("model.timeout", {"round": round_no, "seconds": self.timeout})

            return _final("Модель не ответила вовремя. Запрос не выполнен, это записано в журнал.")

        calls = [
            content.name
            for message in response.messages
            for content in message.contents
            if content.type == "function_call"
        ]
        usage = response.usage_details or {}

        self.journal.write(
            "model.call",
            {
                "round": round_no,
                "ms": int((time.perf_counter() - started) * 1000),
                "tokensIn": usage.get("input_token_count"),
                "tokensOut": usage.get("output_token_count"),
                "toolCalls": calls,
                "text": _short(response.text),
            },
        )

        print(
            "[2 модель] ответ текстом"
            if not calls
            else f"[2 модель] просит инструмент: {', '.join(calls)}"
        )

        return response


def _final(text: str) -> ChatResponse:
    """Готовый ответ без вызовов инструментов: цикл примет его как финальный."""
    return ChatResponse(messages=[Message("assistant", [Content.from_text(text)])])


def _short(text: str) -> str:
    return text[:160] + "…" if len(text) > 160 else text
