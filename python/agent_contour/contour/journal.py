"""Журнал — что система сделала на самом деле.

Не стенограмма разговора, а запись действий: каждый вызов модели, каждый вызов
инструмента с аргументами и результатом, каждый отказ. Одна строка JSON
на событие, файл можно читать глазами и grep'ом.

Правило из доклада: сделано только то, что есть в журнале.
"""

import json
import threading
from datetime import datetime
from pathlib import Path


class Journal:
    def __init__(self, path: Path) -> None:
        self.path = path
        # Один писатель за раз: журнал тоже состояние, и у него один путь записи
        self._gate = threading.Lock()

    def write(self, kind: str, data: dict) -> None:
        line = json.dumps(
            {"at": datetime.now().strftime("%H:%M:%S.%f")[:-3], "kind": kind, "data": data},
            ensure_ascii=False,
        )

        with self._gate, self.path.open("a", encoding="utf-8") as file:
            file.write(line + "\n")
