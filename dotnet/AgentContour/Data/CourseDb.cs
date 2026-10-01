// Владелец фактов и состояния.
//
// Косяк 1: у факта есть владелец. Дедлайны живут здесь, модель их не знает
// и не должна знать, она спрашивает инструмент.
//
// Косяк 5: один владелец, один путь записи. Всё, что меняет состояние,
// проходит через Mutate под блокировкой и сразу сохраняется на диск.
// Читать можно параллельно, записывать по очереди.

namespace AgentContour.Data;

public sealed class CourseDb
{
	private readonly Lock gate = new();
	private readonly string statePath;
	private State state;

	public CourseDb(string statePath)
	{
		this.statePath = statePath;
		state = File.Exists(statePath)
			? JsonSerializer.Deserialize<State>(File.ReadAllText(statePath)) ?? new State()
			: new State();
	}

	// Факты. Модуля M-03 здесь нарочно нет: это тот самый несуществующий дедлайн
	private static readonly Dictionary<string, DateOnly> Deadlines = new()
	{
		["M-01"] = new DateOnly(2026, 10, 15),
		["M-02"] = new DateOnly(2026, 11, 1),
	};

	public IEnumerable<string> ModuleIds => Deadlines.Keys;

	public DateOnly? GetDeadline(string moduleId) =>
		Deadlines.TryGetValue(moduleId.Trim().ToUpperInvariant(), out var date) ? date : null;

	public IReadOnlyList<string> Reminders
	{
		get
		{
			lock (gate)
			{
				return [.. state.Reminders];
			}
		}
	}

	public void AddReminder(string text) => Mutate(s => s.Reminders.Add(text));

	// Единственный путь записи: взять блокировку, изменить, сохранить
	private void Mutate(Action<State> change)
	{
		lock (gate)
		{
			change(state);
			File.WriteAllText(statePath, JsonSerializer.Serialize(state, Hooks.Json.Options));
		}
	}

	private sealed class State
	{
		public List<string> Reminders { get; set; } = [];
	}
}
