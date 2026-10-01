// [4] После ответа: проверка до того, как текст увидит человек.
//
// Запрет проверяет код (косяк 2). Инструкция может попросить модель не выдавать
// секреты, но держит только проверка снаружи: что бы модель ни согласилась
// «дописать», наружу это не уйдёт. Нашли секрет — заменяем ответ целиком:
// вырезать «только его» ненадёжно, секрет мог принять неожиданную форму.
package contour

import (
	"fmt"
	"regexp"
	"strings"
)

var (
	// Промокоды школы выглядят так: SHKOLA-50, SHKOLA-100
	promoCode  = regexp.MustCompile(`(?i)\bSHKOLA-\d{2,3}\b`)
	cardNumber = regexp.MustCompile(`\b\d{4}[ -]?\d{4}[ -]?\d{4}[ -]?\d{4}\b`)
	thinkBlock = regexp.MustCompile(`(?s)<think>.*?</think>`)
)

type AfterAnswer struct {
	journal *Journal
}

func NewAfterAnswer(journal *Journal) *AfterAnswer {
	return &AfterAnswer{journal: journal}
}

func (a *AfterAnswer) Check(text string) string {
	// Модели с рассуждениями иногда отдают их в самом тексте
	text = strings.TrimSpace(thinkBlock.ReplaceAllString(text, ""))

	if text == "" {
		a.journal.Write("answer.empty", map[string]any{})
		return "Ответа не получилось. Попробуйте переформулировать вопрос."
	}

	if kind := findSecret(text); kind != "" {
		fmt.Printf("[4 выход] в ответе %s — ответ скрыт целиком\n", kind)
		a.journal.Write("answer.blocked", map[string]any{"reason": kind})

		return "[Ответ скрыт: в нём оказались служебные данные школы.]"
	}

	fmt.Println("[4 выход] ответ чист")
	a.journal.Write("answer", map[string]any{"text": text})

	return text
}

func findSecret(text string) string {
	switch {
	case promoCode.MatchString(text):
		return "промокод"
	case cardNumber.MatchString(text):
		return "номер карты"
	}
	return ""
}
