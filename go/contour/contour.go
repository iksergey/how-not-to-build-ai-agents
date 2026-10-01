// Пакет contour — ваш код вокруг модели (слайд 7: модель думает, контур решает).
// Четыре точки вставки собраны в одну структуру, чтобы цикл в agent.go читался.
package contour

type Contour struct {
	Before      *BeforeModel
	AroundModel *AroundModel
	AroundTool  *AroundTool
	After       *AfterAnswer
}

func short(text string, n int) string {
	runes := []rune(text)
	if len(runes) > n {
		return string(runes[:n]) + "…"
	}
	return text
}
