// Цикл агента — слайд 5. Написан руками, чтобы было видно, где что происходит.
//
// Фреймворк умеет то же самое одной строкой (openaiprovider.NewChatCompletionsAgent
// из agent-framework-go), и в бою так и делают. Но пока цикл спрятан внутри,
// непонятно, куда вставлять свой код. Здесь он на виду, и четыре точки вставки
// (слайд 6) обозначены цифрами.
package main

import (
	"context"

	"github.com/openai/openai-go/v3"

	"agentcontour/contour"
	"agentcontour/tools"
)

type Agent struct {
	client  *openai.Client
	model   string
	tools   *tools.Set
	contour contour.Contour

	// Разговор, который помнит агент. Память ведём мы, а не модель:
	// она каждый раз видит только то, что мы ей покажем
	history []openai.ChatCompletionMessageParamUnion
}

func NewAgent(client *openai.Client, model string, toolset *tools.Set, c contour.Contour) *Agent {
	return &Agent{client: client, model: model, tools: toolset, contour: c}
}

func (a *Agent) Run(ctx context.Context, request string) (string, error) {
	// [1] До вызова модели: собираем контекст. Инструкция, факты от владельца,
	//     хвост разговора — всё, что модель увидит. Больше она не знает ничего
	messages := a.contour.Before.Build(a.history, request)

	params := openai.ChatCompletionNewParams{
		Model: a.model,
		Tools: a.tools.Definitions(),
	}

	for round := 1; ; round++ {
		params.Messages = messages

		// [2] Вокруг вызова модели: срок, предел кругов, запись в журнал.
		//     Если срок вышел или кругов слишком много — приходит готовый
		//     ответ без вызовов инструментов, и цикл заканчивается штатно
		reply, err := a.contour.AroundModel.Call(ctx, a.client, params, round)
		if err != nil {
			return "", err
		}

		messages = append(messages, reply.ToParam())

		// Нужен инструмент? Нет — отдаём ответ человеку
		if len(reply.ToolCalls) == 0 {
			// [4] После ответа: тема, формат, секреты. Только потом наружу
			answer := a.contour.After.Check(reply.Content)

			a.history = append(a.history,
				openai.UserMessage(request),
				openai.AssistantMessage(answer),
			)
			return answer, nil
		}

		// Да — вызываем. Модель только ПРЕДЛОЖИЛА вызов; решает код
		for _, call := range reply.ToolCalls {
			// [3] Вокруг вызова инструмента: права, подтверждение необратимого,
			//     журнал. Результат уходит обратно в модель, и круг повторяется
			result := a.contour.AroundTool.Invoke(a.tools, call.Function.Name, call.Function.Arguments)

			messages = append(messages, openai.ToolMessage(result, call.ID))
		}
	}
}
