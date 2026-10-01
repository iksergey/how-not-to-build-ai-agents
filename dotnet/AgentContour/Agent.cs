// Цикл агента — слайд 5. Написан руками, чтобы было видно, где что происходит.
//
// Фреймворк умеет то же самое одной строкой (chatClient.AsAIAgent(...)), и в бою
// так и делают. Но пока цикл спрятан внутри, непонятно, куда вставлять свой код.
// Здесь он на виду, и четыре точки вставки (слайд 6) обозначены цифрами.

using AgentContour.Hooks;

namespace AgentContour;

public sealed class Agent(
	IChatClient model,
	IReadOnlyList<AIFunction> tools,
	Contour contour
)
{
	// Разговор, который помнит агент. Память ведём мы, а не модель:
	// она каждый раз видит только то, что мы ей покажем
	private readonly List<ChatMessage> history = [];

	public async Task<string> RunAsync(string request, CancellationToken ct = default)
	{
		// [1] До вызова модели: собираем контекст. Инструкция, факты от владельца,
		//     хвост разговора — всё, что модель увидит. Больше она не знает ничего
		List<ChatMessage> messages = contour.Before.Build(history, request);

		var options = new ChatOptions
		{
			Tools = [.. tools],
			ToolMode = ChatToolMode.Auto,
		};

		for (var round = 1; ; round++)
		{
			// [2] Вокруг вызова модели: срок, предел кругов, запись в журнал.
			//     Если срок вышел или кругов слишком много — приходит готовый
			//     ответ без вызовов инструментов, и цикл заканчивается штатно
			ChatResponse response = await contour.AroundModel.CallAsync(
				model,
				messages,
				options,
				round,
				ct
			);

			messages.AddRange(response.Messages);

			List<FunctionCallContent> calls =
			[
				.. response.Messages.SelectMany(m => m.Contents).OfType<FunctionCallContent>(),
			];

			// Нужен инструмент? Нет — отдаём ответ человеку
			if (calls.Count == 0)
			{
				// [4] После ответа: тема, формат, секреты. Только потом наружу
				var answer = contour.After.Check(response.Text);

				history.Add(new ChatMessage(ChatRole.User, request));
				history.Add(new ChatMessage(ChatRole.Assistant, answer));

				return answer;
			}

			// Да — вызываем. Модель только ПРЕДЛОЖИЛА вызов; решает код
			foreach (FunctionCallContent call in calls)
			{
				// [3] Вокруг вызова инструмента: права, подтверждение необратимого,
				//     журнал. Результат уходит обратно в модель, и круг повторяется
				var result = await contour.AroundTool.InvokeAsync(tools, call, ct);

				messages.Add(
					new ChatMessage(ChatRole.Tool, [new FunctionResultContent(call.CallId, result)])
				);
			}
		}
	}
}
