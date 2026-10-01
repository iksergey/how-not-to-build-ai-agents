// Пакет tools — руки агента. Отдавая инструмент модели, вы отдаёте ей свои
// права целиком, поэтому границы стоят внутри инструмента, а не в инструкции.
//
// Описания читает модель: по ним она решает, что и когда звать. Тексты
// результатов читает тоже модель, поэтому пустых ответов нет: «не найдено»
// говорим словами, иначе модель заполнит пустоту сама.
package tools

import (
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
	"strings"

	"github.com/openai/openai-go/v3"
	"github.com/openai/openai-go/v3/shared"

	"agentcontour/data"
)

// Tool — описание для модели плюс исполнитель. Аргументы приходят строкой JSON,
// как их прислала модель: разбираем сами и сами же проверяем
type Tool struct {
	Name        string
	Description string
	Params      map[string]any
	Run         func(args string) (string, error)
}

type Set struct {
	list []Tool
}

func (s *Set) Find(name string) (Tool, bool) {
	for _, t := range s.list {
		if t.Name == name {
			return t, true
		}
	}
	return Tool{}, false
}

// Definitions — то, что уходит модели: имена, описания, схемы аргументов
func (s *Set) Definitions() []openai.ChatCompletionToolUnionParam {
	defs := make([]openai.ChatCompletionToolUnionParam, 0, len(s.list))
	for _, t := range s.list {
		defs = append(defs, openai.ChatCompletionFunctionTool(shared.FunctionDefinitionParam{
			Name:        t.Name,
			Description: openai.String(t.Description),
			Parameters:  t.Params,
		}))
	}
	return defs
}

func New(db *data.CourseDb, workspace string) *Set {
	buildChecks := 0

	return &Set{list: []Tool{
		{
			Name:        "get_module_deadline",
			Description: "Возвращает дедлайн модуля курса по его коду. Единственный источник дат.",
			Params:      object("module_id", "Код модуля: буква M, дефис и две цифры, например M-02"),
			Run: func(args string) (string, error) {
				var in struct {
					ModuleID string `json:"module_id"`
				}
				if err := json.Unmarshal([]byte(args), &in); err != nil {
					return "", err
				}
				// Косяк 1: «не знаю» говорит код. Модель получает это текстом и передаёт дальше
				date, ok := db.Deadline(in.ModuleID)
				if !ok {
					return fmt.Sprintf("Модуля %s нет в плане курса. Дедлайна у него нет.", in.ModuleID), nil
				}
				return fmt.Sprintf("Дедлайн модуля %s: %s.", strings.ToUpper(in.ModuleID), date.Format("02.01.2006")), nil
			},
		},
		{
			Name:        "save_note",
			Description: "Сохраняет текстовую заметку в файл в рабочей папке агента.",
			Params: object(
				"file_name", "Имя файла, например заметки/дедлайны.txt",
				"text", "Текст заметки",
			),
			Run: func(args string) (string, error) {
				var in struct {
					FileName string `json:"file_name"`
					Text     string `json:"text"`
				}
				if err := json.Unmarshal([]byte(args), &in); err != nil {
					return "", err
				}
				// Косяк 3: граница в инструменте. Путь сверяется с рабочей папкой ДО записи.
				// «../../../важное.txt» после нормализации окажется выше — отказ
				full := filepath.Clean(filepath.Join(workspace, in.FileName))
				if !strings.HasPrefix(full, workspace+string(filepath.Separator)) {
					return fmt.Sprintf("ОТКАЗ: путь «%s» ведёт за пределы рабочей папки. "+
						"Записывать можно только внутрь неё.", in.FileName), nil
				}
				if err := os.MkdirAll(filepath.Dir(full), 0o755); err != nil {
					return "", err
				}
				if err := os.WriteFile(full, []byte(in.Text), 0o644); err != nil {
					return "", err
				}
				rel, _ := filepath.Rel(workspace, full)
				return fmt.Sprintf("Заметка сохранена: %s.", rel), nil
			},
		},
		{
			Name:        "add_reminder",
			Description: "Записывает напоминание в список дел куратора. Вызывать, когда просят что-то запомнить.",
			Params:      object("text", "Текст напоминания"),
			Run: func(args string) (string, error) {
				var in struct {
					Text string `json:"text"`
				}
				if err := json.Unmarshal([]byte(args), &in); err != nil {
					return "", err
				}
				// Косяк 4 и 5: запись идёт через единственный путь владельца состояния.
				// Результат проверяем не по словам агента, а по state.json и журналу
				if err := db.AddReminder(in.Text); err != nil {
					return "", err
				}
				return fmt.Sprintf("Напоминание записано. Всего напоминаний: %d.", len(db.Reminders())), nil
			},
		},
		{
			Name:        "get_staff_benefits",
			Description: "Возвращает служебную справку о льготах для сотрудников школы.",
			Params:      object(),
			Run: func(string) (string, error) {
				// Данные приносят секрет с собой: так утечки и случаются в жизни. Не модель
				// его выдумала, он лежал в справке. Ловит проверка на выходе (AfterAnswer)
				return "Сотрудникам: бесплатный доступ ко всем курсам, скидка близким по промокоду SHKOLA-50, " +
					"отпуск на обучение 5 дней в год.", nil
			},
		},
		{
			Name:        "get_build_status",
			Description: "Возвращает состояние сборки задачи по её номеру, например build-42.",
			Params:      object("build_id", "Номер задачи"),
			Run: func(args string) (string, error) {
				var in struct {
					BuildID string `json:"build_id"`
				}
				if err := json.Unmarshal([]byte(args), &in); err != nil {
					return "", err
				}
				// Косяк 6: инструмент нарочно никогда не заканчивает. Если модель будет
				// звать его по кругу, остановит предел кругов в AroundModel, а не удача
				buildChecks++
				return fmt.Sprintf("Сборка %s ещё выполняется (проверка №%d). "+
					"Результата пока нет, вызовите get_build_status снова.", in.BuildID, buildChecks), nil
			},
		},
		{
			Name:        "publish_module",
			Description: "Открывает модуль курса всем студентам. Действие необратимо.",
			Params:      object("module_id", "Код модуля: буква M, дефис и две цифры, например M-02"),
			Run: func(args string) (string, error) {
				var in struct {
					ModuleID string `json:"module_id"`
				}
				if err := json.Unmarshal([]byte(args), &in); err != nil {
					return "", err
				}
				// Сюда код попадает только после подтверждения человеком (AroundTool)
				if _, ok := db.Deadline(in.ModuleID); !ok {
					return fmt.Sprintf("ОТКАЗ: модуля %s нет в плане курса, публиковать нечего.", in.ModuleID), nil
				}
				return fmt.Sprintf("Модуль %s опубликован для всех студентов.", strings.ToUpper(in.ModuleID)), nil
			},
		},
	}}
}

// object собирает JSON-схему объекта из пар «имя — описание», все поля строковые и обязательные
func object(pairs ...string) map[string]any {
	props := map[string]any{}
	required := []string{}
	for i := 0; i+1 < len(pairs); i += 2 {
		props[pairs[i]] = map[string]any{"type": "string", "description": pairs[i+1]}
		required = append(required, pairs[i])
	}
	return map[string]any{"type": "object", "properties": props, "required": required}
}
