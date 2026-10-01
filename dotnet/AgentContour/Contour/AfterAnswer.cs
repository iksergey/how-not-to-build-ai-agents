// [4] После ответа: проверка до того, как текст увидит человек.
//
// Запрет проверяет код (косяк 2). Инструкция может попросить модель не выдавать
// секреты, но держит только проверка снаружи: что бы модель ни согласилась
// «дописать», наружу это не уйдёт. Нашли секрет — заменяем ответ целиком:
// вырезать «только его» ненадёжно, секрет мог принять неожиданную форму.

namespace AgentContour.Hooks;

public sealed partial class AfterAnswer(Journal journal)
{
	public string Check(string text)
	{
		// Модели с рассуждениями иногда отдают их в самом тексте
		text = ThinkBlock().Replace(text, "").Trim();

		if (string.IsNullOrWhiteSpace(text))
		{
			journal.Write("answer.empty", new { });
			return "Ответа не получилось. Попробуйте переформулировать вопрос.";
		}

		if (FindSecret(text) is { } kind)
		{
			Console.WriteLine($"[4 выход] в ответе {kind} — ответ скрыт целиком");
			journal.Write("answer.blocked", new { reason = kind });

			return "[Ответ скрыт: в нём оказались служебные данные школы.]";
		}

		Console.WriteLine("[4 выход] ответ чист");
		journal.Write("answer", new { text });

		return text;
	}

	private static string? FindSecret(string text) =>
		PromoCode().IsMatch(text) ? "промокод"
		: CardNumber().IsMatch(text) ? "номер карты"
		: null;

	// Промокоды школы выглядят так: SHKOLA-50, SHKOLA-100
	[GeneratedRegex(@"\bSHKOLA-\d{2,3}\b", RegexOptions.IgnoreCase)]
	private static partial Regex PromoCode();

	[GeneratedRegex(@"\b\d{4}[ -]?\d{4}[ -]?\d{4}[ -]?\d{4}\b")]
	private static partial Regex CardNumber();

	[GeneratedRegex(@"<think>.*?</think>", RegexOptions.Singleline)]
	private static partial Regex ThinkBlock();
}
