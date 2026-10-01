// [2] Вокруг вызова модели: срок, предел кругов, журнал.
//
// У всего есть конец (косяк 6). Модель может молчать — тогда срок. Модель
// может бесконечно звать инструмент — тогда предел кругов. В обоих случаях
// цикл получает обычный ответ без вызовов и заканчивается штатно, а в
// журнале остаётся запись, почему.
package contour

import (
	"context"
	"errors"
	"fmt"
	"strings"
	"time"

	"github.com/openai/openai-go/v3"
)

type AroundModel struct {
	journal   *Journal
	timeout   time.Duration
	maxRounds int
}

func NewAroundModel(journal *Journal, timeout time.Duration, maxRounds int) *AroundModel {
	return &AroundModel{journal: journal, timeout: timeout, maxRounds: maxRounds}
}

func (m *AroundModel) Call(ctx context.Context, client *openai.Client, params openai.ChatCompletionNewParams, round int) (openai.ChatCompletionMessage, error) {
	if round > m.maxRounds {
		fmt.Printf("[2 модель] круг %d: предел %d исчерпан, останавливаем\n", round, m.maxRounds)
		m.journal.Write("model.stop", map[string]any{"round": round, "reason": "предел кругов"})

		return final(fmt.Sprintf("Не удалось закончить за %d шагов. Задача не выполнена, "+
			"это записано в журнал. Попробуйте позже или уточните запрос.", m.maxRounds)), nil
	}

	fmt.Printf("[2 модель] круг %d: вызов, срок %.0f с\n", round, m.timeout.Seconds())

	ctx, cancel := context.WithTimeout(ctx, m.timeout)
	defer cancel()

	started := time.Now()

	completion, err := client.Chat.Completions.New(ctx, params)
	if err != nil {
		if errors.Is(err, context.DeadlineExceeded) {
			// Срок вышел. Тишина — не «готово», это записанный итог
			fmt.Printf("[2 модель] срок %.0f с вышел\n", m.timeout.Seconds())
			m.journal.Write("model.timeout", map[string]any{"round": round, "seconds": m.timeout.Seconds()})

			return final("Модель не ответила вовремя. Запрос не выполнен, это записано в журнал."), nil
		}
		return openai.ChatCompletionMessage{}, fmt.Errorf("вызов модели: %w", err)
	}

	if len(completion.Choices) == 0 {
		return final("Модель вернула пустой ответ. Это записано в журнал."), nil
	}

	reply := completion.Choices[0].Message

	calls := make([]string, 0, len(reply.ToolCalls))
	for _, call := range reply.ToolCalls {
		calls = append(calls, call.Function.Name)
	}

	m.journal.Write("model.call", map[string]any{
		"round":     round,
		"ms":        time.Since(started).Milliseconds(),
		"tokensIn":  completion.Usage.PromptTokens,
		"tokensOut": completion.Usage.CompletionTokens,
		"toolCalls": calls,
		"text":      short(reply.Content, 160),
	})

	if len(calls) == 0 {
		fmt.Println("[2 модель] ответ текстом")
	} else {
		fmt.Printf("[2 модель] просит инструмент: %s\n", strings.Join(calls, ", "))
	}

	return reply, nil
}

// Готовый ответ без вызовов инструментов: цикл примет его как финальный
func final(text string) openai.ChatCompletionMessage {
	return openai.ChatCompletionMessage{Role: "assistant", Content: text}
}
