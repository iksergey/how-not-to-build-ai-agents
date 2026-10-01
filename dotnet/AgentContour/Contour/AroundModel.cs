// [2] Вокруг вызова модели: срок, предел кругов, журнал.
//
// У всего есть конец (косяк 6). Модель может молчать — тогда срок. Модель
// может бесконечно звать инструмент — тогда предел кругов. В обоих случаях
// цикл получает обычный ответ без вызовов и заканчивается штатно, а в
// журнале остаётся запись, почему.

namespace AgentContour.Hooks;

public sealed class AroundModel(Journal journal, TimeSpan timeout, int maxRounds)
{
	public async Task<ChatResponse> CallAsync(
		IChatClient model,
		List<ChatMessage> messages,
		ChatOptions options,
		int round,
		CancellationToken ct
	)
	{
		if (round > maxRounds)
		{
			Console.WriteLine($"[2 модель] круг {round}: предел {maxRounds} исчерпан, останавливаем");
			journal.Write("model.stop", new { round, reason = "предел кругов" });

			return Final(
				$"Не удалось закончить за {maxRounds} шагов. Задача не выполнена, "
					+ "это записано в журнал. Попробуйте позже или уточните запрос."
			);
		}

		Console.WriteLine($"[2 модель] круг {round}: вызов, срок {timeout.TotalSeconds:0} с");

		using var cts = CancellationTokenSource.CreateLinkedTokenSource(ct);
		cts.CancelAfter(timeout);

		var started = DateTime.UtcNow;

		try
		{
			ChatResponse response = await model.GetResponseAsync(messages, options, cts.Token);

			var calls = response
				.Messages.SelectMany(m => m.Contents)
				.OfType<FunctionCallContent>()
				.Select(c => c.Name)
				.ToList();

			journal.Write(
				"model.call",
				new
				{
					round,
					ms = (int)(DateTime.UtcNow - started).TotalMilliseconds,
					tokensIn = response.Usage?.InputTokenCount,
					tokensOut = response.Usage?.OutputTokenCount,
					toolCalls = calls,
					text = Short(response.Text),
				}
			);

			Console.WriteLine(
				calls.Count == 0
					? "[2 модель] ответ текстом"
					: $"[2 модель] просит инструмент: {string.Join(", ", calls)}"
			);

			return response;
		}
		catch (OperationCanceledException) when (!ct.IsCancellationRequested)
		{
			// Срок вышел. Тишина — не «готово», это записанный итог
			Console.WriteLine($"[2 модель] срок {timeout.TotalSeconds:0} с вышел");
			journal.Write("model.timeout", new { round, seconds = timeout.TotalSeconds });

			return Final("Модель не ответила вовремя. Запрос не выполнен, это записано в журнал.");
		}
	}

	// Готовый ответ без вызовов инструментов: цикл примет его как финальный
	private static ChatResponse Final(string text) =>
		new([new ChatMessage(ChatRole.Assistant, text)]);

	private static string Short(string text) =>
		text.Length > 160 ? text[..160] + "…" : text;
}
