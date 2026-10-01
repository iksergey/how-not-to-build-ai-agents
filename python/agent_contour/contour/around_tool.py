"""[3] Вокруг вызова инструмента: права, подтверждение, журнал.

Модель только предложила вызов. Здесь решает код:
  - есть ли такой инструмент вообще (права — это список, который дали модели);
  - необратимое подтверждает человек, а не модель;
  - каждый вызов с аргументами и результатом ложится в журнал. Это квитанция:
    сделано только то, что здесь записано (косяк 4).

Границы самих действий (путь внутри рабочей папки, формат кода модуля)
проверяют инструменты: граница живёт в инструменте (косяк 3), а не в
инструкции модели.
"""

import json
import sys
from collections.abc import Callable

from agent_framework import Content, FunctionTool

from agent_contour.contour.journal import Journal

# Инструменты, после которых не отмотать назад. Их список — решение
# архитектора, модель на него не влияет
IRREVERSIBLE = {"publish_module"}


class AroundTool:
    def __init__(self, journal: Journal, approve: Callable[[str, str], bool]) -> None:
        self.journal = journal
        self.approve = approve

    async def invoke(self, tools: list[FunctionTool], call: Content) -> str:
        arguments = call.parse_arguments() or {}
        args = json.dumps(arguments, ensure_ascii=False)

        tool = next((t for t in tools if t.name == call.name), None)

        if tool is None:
            # Модель попросила то, чего ей не давали. Не падаем: отвечаем
            # текстом, модель передаст отказ пользователю
            return self._done(call, args, "ОТКАЗ: такого инструмента нет", "no-such-tool")

        if call.name in IRREVERSIBLE and not self.approve(call.name, args):
            return self._done(call, args, "ОТКАЗ: человек не подтвердил действие", "declined")

        try:
            result = await tool.invoke(arguments=arguments, skip_parsing=True)
            return self._done(call, args, str(result), "ok")
        except Exception as error:  # noqa: BLE001 — ошибка инструмента тоже результат
            # Молча проглотить её — значит получить «сделано», которого не было
            return self._done(call, args, f"ОШИБКА: {error}", "error")

    def _done(self, call: Content, args: str, result: str, status: str) -> str:
        print(f"[3 инструмент] {call.name}({args}) → {_short(result)}")

        self.journal.write(
            "tool.call", {"tool": call.name, "args": args, "status": status, "result": result}
        )

        return result


def ask_human(tool: str, args: str) -> bool:
    """Подтверждение человеком. Без терминала отвечаем «нет»:
    отказ дешевле, чем необратимое действие без спроса."""
    print(f"[подтверждение] агент хочет вызвать {tool} с {args}")

    if not sys.stdin.isatty():
        print("[подтверждение] терминала нет — отказ по умолчанию")
        return False

    answer = input("[подтверждение] Разрешить? Y/N: ").strip().upper()
    return answer in {"Y", "Д"}


def _short(text: str) -> str:
    line = " ".join(text.splitlines())
    return line[:120] + "…" if len(line) > 120 else line
