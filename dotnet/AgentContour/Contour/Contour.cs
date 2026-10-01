// Контур — ваш код вокруг модели (слайд 7: модель думает, контур решает).
// Четыре точки вставки собраны в один объект, чтобы цикл в Agent.cs читался.

namespace AgentContour.Hooks;

public sealed record Contour(
	BeforeModel Before,
	AroundModel AroundModel,
	AroundTool AroundTool,
	AfterAnswer After
);
