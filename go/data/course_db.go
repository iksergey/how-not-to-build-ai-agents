// Пакет data — владелец фактов и состояния.
//
// Косяк 1: у факта есть владелец. Дедлайны живут здесь, модель их не знает
// и не должна знать, она спрашивает инструмент.
//
// Косяк 5: один владелец, один путь записи. Всё, что меняет состояние,
// проходит через mutate под блокировкой и сразу сохраняется на диск.
// Читать можно параллельно, записывать по очереди.
package data

import (
	"encoding/json"
	"errors"
	"os"
	"sort"
	"strings"
	"sync"
	"time"
)

// Факты. Модуля M-03 здесь нарочно нет: это тот самый несуществующий дедлайн
var deadlines = map[string]time.Time{
	"M-01": time.Date(2026, 10, 15, 0, 0, 0, 0, time.Local),
	"M-02": time.Date(2026, 11, 1, 0, 0, 0, 0, time.Local),
}

type state struct {
	Reminders []string `json:"reminders"`
}

type CourseDb struct {
	statePath string
	mu        sync.RWMutex
	state     state
}

func NewCourseDb(statePath string) (*CourseDb, error) {
	db := &CourseDb{statePath: statePath, state: state{Reminders: []string{}}}

	raw, err := os.ReadFile(statePath)
	switch {
	case errors.Is(err, os.ErrNotExist):
		return db, nil
	case err != nil:
		return nil, err
	}
	if err := json.Unmarshal(raw, &db.state); err != nil {
		return nil, err
	}
	return db, nil
}

func (db *CourseDb) ModuleIDs() []string {
	ids := make([]string, 0, len(deadlines))
	for id := range deadlines {
		ids = append(ids, id)
	}
	sort.Strings(ids)
	return ids
}

func (db *CourseDb) Deadline(moduleID string) (time.Time, bool) {
	date, ok := deadlines[strings.ToUpper(strings.TrimSpace(moduleID))]
	return date, ok
}

func (db *CourseDb) Reminders() []string {
	db.mu.RLock()
	defer db.mu.RUnlock()
	return append([]string(nil), db.state.Reminders...)
}

func (db *CourseDb) AddReminder(text string) error {
	return db.mutate(func(s *state) { s.Reminders = append(s.Reminders, text) })
}

// Единственный путь записи: взять блокировку, изменить, сохранить
func (db *CourseDb) mutate(change func(*state)) error {
	db.mu.Lock()
	defer db.mu.Unlock()

	change(&db.state)

	raw, err := json.MarshalIndent(db.state, "", "  ")
	if err != nil {
		return err
	}
	return os.WriteFile(db.statePath, raw, 0o644)
}
