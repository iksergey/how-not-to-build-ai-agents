"""Контур — ваш код вокруг модели (слайд 7: модель думает, контур решает).

Четыре точки вставки собраны в один объект, чтобы цикл в agent.py читался.
"""

from dataclasses import dataclass

from agent_contour.contour.after_answer import AfterAnswer
from agent_contour.contour.around_model import AroundModel
from agent_contour.contour.around_tool import AroundTool
from agent_contour.contour.before_model import BeforeModel


@dataclass(frozen=True)
class Contour:
    before: BeforeModel
    around_model: AroundModel
    around_tool: AroundTool
    after: AfterAnswer


__all__ = ["AfterAnswer", "AroundModel", "AroundTool", "BeforeModel", "Contour"]
