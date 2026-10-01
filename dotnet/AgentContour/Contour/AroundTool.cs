// [3] Вокруг вызова инструмента: права, подтверждение, журнал.
//
// Модель только предложила вызов. Здесь решает код:
//   - есть ли такой инструмент вообще (права — это список, который дали модели);
//   - необратимое подтверждает человек, а не модель;
//   - каждый вызов с аргументами и результатом ложится в журнал. Это квитанция:
//     сделано только то, что здесь записано (косяк 4).
//
// Границы самих действий (путь внутри рабочей папки, формат кода модуля)
// проверяют инструменты: граница живёт в инструменте (косяк 3), а не в
// инструкции модели.

namespace AgentContour.Hooks;

public sealed class AroundTool(Journal journal, Func<string, string, bool> approve)
{
	// Инструменты, после которых не отмотать назад. Их список — решение
	// архитектора, модель на него не влияет
	private static readonly HashSet<string> Irreversible = ["PublishModule"];

	public async Task<string> InvokeAsync(
		IReadOnlyList<AIFunction> tools,
		FunctionCallContent call,
		CancellationToken ct
	)
	{
		var args = JsonSerializer.Serialize(call.Arguments ?? new Dictionary<string, object?>(), Json.Options);

		AIFunction? tool = tools.FirstOrDefault(t => t.Name == call.Name);

		if (tool is null)
		{
			// Модель попросила то, чего ей не давали. Не падаем: отвечаем
			// текстом, модель передаст отказ пользователю
			return Done(call, args, "ОТКАЗ: такого инструмента нет", "no-such-tool");
		}

		if (Irreversible.Contains(call.Name) && !approve(call.Name, args))
		{
			return Done(call, args, "ОТКАЗ: человек не подтвердил действие", "declined");
		}

		try
		{
			object? result = await tool.InvokeAsync(new AIFunctionArguments(call.Arguments), ct);

			return Done(call, args, result?.ToString() ?? "", "ok");
		}
		catch (Exception ex)
		{
			// Ошибка инструмента — тоже результат, и тоже в журнал.
			// Молча проглотить её — значит получить «сделано», которого не было
			return Done(call, args, $"ОШИБКА: {ex.Message}", "error");
		}
	}

	private string Done(FunctionCallContent call, string args, string result, string status)
	{
		Console.WriteLine($"[3 инструмент] {call.Name}({args}) → {Short(result)}");

		journal.Write("tool.call", new { tool = call.Name, args, status, result });

		return result;
	}

	private static string Short(string text)
	{
		var line = text.ReplaceLineEndings(" ");
		return line.Length > 120 ? line[..120] + "…" : line;
	}
}

// Подтверждение человеком. В сценарии без терминала (ввод перенаправлен)
// отвечаем «нет»: отказ дешевле, чем необратимое действие без спроса
public static class Approval
{
	public static bool Ask(string tool, string args)
	{
		Console.WriteLine($"[подтверждение] агент хочет вызвать {tool} с {args}");

		if (Console.IsInputRedirected)
		{
			Console.WriteLine("[подтверждение] терминала нет — отказ по умолчанию");
			return false;
		}

		Console.Write("[подтверждение] Разрешить? Y/N: ");
		var answer = Console.ReadLine()?.Trim().ToUpperInvariant();

		return answer is "Y" or "Д";
	}
}
