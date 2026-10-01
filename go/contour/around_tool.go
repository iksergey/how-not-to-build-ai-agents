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
package contour

import (
	"bufio"
	"fmt"
	"os"
	"strings"

	"agentcontour/tools"
)

// Инструменты, после которых не отмотать назад. Их список — решение
// архитектора, модель на него не влияет
var irreversible = map[string]bool{"publish_module": true}

type AroundTool struct {
	journal *Journal
	approve func(tool, args string) bool
}

func NewAroundTool(journal *Journal, approve func(tool, args string) bool) *AroundTool {
	return &AroundTool{journal: journal, approve: approve}
}

func (t *AroundTool) Invoke(set *tools.Set, name, args string) string {
	tool, ok := set.Find(name)
	if !ok {
		// Модель попросила то, чего ей не давали. Не падаем: отвечаем
		// текстом, модель передаст отказ пользователю
		return t.done(name, args, "ОТКАЗ: такого инструмента нет", "no-such-tool")
	}

	if irreversible[name] && !t.approve(name, args) {
		return t.done(name, args, "ОТКАЗ: человек не подтвердил действие", "declined")
	}

	result, err := tool.Run(args)
	if err != nil {
		// Ошибка инструмента — тоже результат, и тоже в журнал.
		// Молча проглотить её — значит получить «сделано», которого не было
		return t.done(name, args, "ОШИБКА: "+err.Error(), "error")
	}

	return t.done(name, args, result, "ok")
}

func (t *AroundTool) done(name, args, result, status string) string {
	fmt.Printf("[3 инструмент] %s(%s) → %s\n", name, args, short(strings.ReplaceAll(result, "\n", " "), 120))

	t.journal.Write("tool.call", map[string]any{"tool": name, "args": args, "status": status, "result": result})

	return result
}

// AskHuman — подтверждение человеком. Без терминала (ввод перенаправлен)
// отвечаем «нет»: отказ дешевле, чем необратимое действие без спроса
func AskHuman(tool, args string) bool {
	fmt.Printf("[подтверждение] агент хочет вызвать %s с %s\n", tool, args)

	if info, err := os.Stdin.Stat(); err != nil || info.Mode()&os.ModeCharDevice == 0 {
		fmt.Println("[подтверждение] терминала нет — отказ по умолчанию")
		return false
	}

	fmt.Print("[подтверждение] Разрешить? Y/N: ")
	scanner := bufio.NewScanner(os.Stdin)
	if !scanner.Scan() {
		fmt.Println("\n[подтверждение] ввода нет — отказ по умолчанию")
		return false
	}
	answer := strings.ToUpper(strings.TrimSpace(scanner.Text()))
	return answer == "Y" || answer == "Д"
}
