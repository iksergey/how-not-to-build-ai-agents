"""[4] После ответа: проверка до того, как текст увидит человек.

Запрет проверяет код (косяк 2). Инструкция может попросить модель не выдавать
секреты, но держит только проверка снаружи: что бы модель ни согласилась
«дописать», наружу это не уйдёт. Нашли секрет — заменяем ответ целиком:
вырезать «только его» ненадёжно, секрет мог принять неожиданную форму.
"""

import re

from agent_contour.contour.journal import Journal

# Промокоды школы выглядят так: SHKOLA-50, SHKOLA-100
PROMO_CODE = re.compile(r"\bSHKOLA-\d{2,3}\b", re.IGNORECASE)
CARD_NUMBER = re.compile(r"\b\d{4}[ -]?\d{4}[ -]?\d{4}[ -]?\d{4}\b")
THINK_BLOCK = re.compile(r"<think>.*?</think>", re.DOTALL)


class AfterAnswer:
    def __init__(self, journal: Journal) -> None:
        self.journal = journal

    def check(self, text: str) -> str:
        # Модели с рассуждениями иногда отдают их в самом тексте
        text = THINK_BLOCK.sub("", text or "").strip()

        if not text:
            self.journal.write("answer.empty", {})
            return "Ответа не получилось. Попробуйте переформулировать вопрос."

        if (kind := _find_secret(text)) is not None:
            print(f"[4 выход] в ответе {kind} — ответ скрыт целиком")
            self.journal.write("answer.blocked", {"reason": kind})

            return "[Ответ скрыт: в нём оказались служебные данные школы.]"

        print("[4 выход] ответ чист")
        self.journal.write("answer", {"text": text})

        return text


def _find_secret(text: str) -> str | None:
    if PROMO_CODE.search(text):
        return "промокод"
    if CARD_NUMBER.search(text):
        return "номер карты"
    return None
