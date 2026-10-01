"""Владелец фактов и состояния.

Косяк 1: у факта есть владелец. Дедлайны живут здесь, модель их не знает
и не должна знать, она спрашивает инструмент.

Косяк 5: один владелец, один путь записи. Всё, что меняет состояние,
проходит через _mutate под блокировкой и сразу сохраняется на диск.
Читать можно параллельно, записывать по очереди.
"""

import json
import threading
from collections.abc import Callable
from datetime import date
from pathlib import Path

# Факты. Модуля M-03 здесь нарочно нет: это тот самый несуществующий дедлайн
DEADLINES = {
    "M-01": date(2026, 10, 15),
    "M-02": date(2026, 11, 1),
}


class CourseDb:
    def __init__(self, state_path: Path) -> None:
        self._state_path = state_path
        self._gate = threading.Lock()
        self._state: dict = (
            json.loads(state_path.read_text(encoding="utf-8"))
            if state_path.exists()
            else {"reminders": []}
        )

    @property
    def module_ids(self) -> list[str]:
        return list(DEADLINES)

    def get_deadline(self, module_id: str) -> date | None:
        return DEADLINES.get(module_id.strip().upper())

    @property
    def reminders(self) -> list[str]:
        with self._gate:
            return list(self._state["reminders"])

    def add_reminder(self, text: str) -> None:
        self._mutate(lambda state: state["reminders"].append(text))

    def _mutate(self, change: Callable[[dict], None]) -> None:
        """Единственный путь записи: взять блокировку, изменить, сохранить."""
        with self._gate:
            change(self._state)
            self._state_path.write_text(
                json.dumps(self._state, ensure_ascii=False, indent=2), encoding="utf-8"
            )
