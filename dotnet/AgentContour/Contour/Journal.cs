// Журнал — что система сделала на самом деле. Не стенограмма разговора,
// а запись действий: каждый вызов модели, каждый вызов инструмента с
// аргументами и результатом, каждый отказ. Одна строка JSON на событие,
// файл можно читать глазами и grep'ом.
//
// Правило из доклада: сделано только то, что есть в журнале.

namespace AgentContour.Hooks;

public sealed class Journal(string path)
{
	private readonly Lock gate = new();

	public string Path { get; } = path;

	public void Write(string kind, object data)
	{
		var line = JsonSerializer.Serialize(
			new
			{
				at = DateTime.Now.ToString("HH:mm:ss.fff"),
				kind,
				data,
			},
			Json.Options
		);

		// Один писатель за раз: журнал тоже состояние, и у него один путь записи
		lock (gate)
		{
			File.AppendAllText(Path, line + Environment.NewLine);
		}
	}
}

public static class Json
{
	public static readonly JsonSerializerOptions Options = new()
	{
		Encoder = System.Text.Encodings.Web.JavaScriptEncoder.UnsafeRelaxedJsonEscaping,
		WriteIndented = false,
	};
}
