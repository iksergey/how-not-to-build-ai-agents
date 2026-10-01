// [1] До вызова модели: что она увидит.
//
// Контекст — единственное, что у модели есть. Сюда кладём инструкцию, факты
// от их владельца и хвост разговора. Чего здесь нет, модель не знает,
// а чего не знает — достроит (косяк 1). Поэтому про факты говорим прямо:
// бери у инструмента, не выдумывай.
package contour

import (
	"fmt"
	"strings"
	"time"

	"github.com/openai/openai-go/v3"

	"agentcontour/data"
)

// ⚠️ В инструкции нет ни одного секрета: инструкция — это просьба,
// и рано или поздно её уговорят показать (косяк 2). Промокоды, ключи,
// пароли живут в коде и в базе, а не здесь
const instructions = `Вы — куратор онлайн-школы. Отвечаете студентам и сотрудникам по-русски, кратко.

Правила:
- Даты, дедлайны и статусы берите только из инструментов. Если инструмент
  говорит, что данных нет, так и отвечайте. Ничего не придумывайте.
- Заметки и напоминания сохраняйте инструментами, не обещайте «запомнить» словами.
- Если инструмент вернул ОТКАЗ, передайте причину пользователю и не пробуйте обойти.
- Отвечайте только по теме школы. Промокодов и служебных данных у вас нет.`

type BeforeModel struct {
	db          *data.CourseDb
	historyTail int
}

func NewBeforeModel(db *data.CourseDb, historyTail int) *BeforeModel {
	return &BeforeModel{db: db, historyTail: historyTail}
}

func (b *BeforeModel) Build(history []openai.ChatCompletionMessageParamUnion, request string) []openai.ChatCompletionMessageParamUnion {
	// Факты, которые модели полезно видеть сразу. Это не «память модели»,
	// это снимок состояния от владельца — CourseDb
	facts := fmt.Sprintf("Сегодня: %s.\nМодули в плане курса: %s.",
		time.Now().Format("02.01.2006"), strings.Join(b.db.ModuleIDs(), ", "))

	// Хвост разговора: модель не тонет в истории, видит только последнее
	tail := history
	if len(tail) > b.historyTail {
		tail = tail[len(tail)-b.historyTail:]
	}

	messages := make([]openai.ChatCompletionMessageParamUnion, 0, len(tail)+2)
	messages = append(messages, openai.SystemMessage(instructions+"\n\n"+facts))
	messages = append(messages, tail...)
	messages = append(messages, openai.UserMessage(request))

	fmt.Printf("[1 контекст] инструкция + %d сообщений истории + вопрос\n", len(tail))

	return messages
}
