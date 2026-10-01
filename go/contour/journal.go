// Журнал — что система сделала на самом деле. Не стенограмма разговора,
// а запись действий: каждый вызов модели, каждый вызов инструмента с
// аргументами и результатом, каждый отказ. Одна строка JSON на событие,
// файл можно читать глазами и grep'ом.
//
// Правило из доклада: сделано только то, что есть в журнале.
package contour

import (
	"encoding/json"
	"log"
	"os"
	"sync"
	"time"
)

type Journal struct {
	Path string
	// Один писатель за раз: журнал тоже состояние, и у него один путь записи
	mu sync.Mutex
}

func NewJournal(path string) *Journal {
	return &Journal{Path: path}
}

func (j *Journal) Write(kind string, data map[string]any) {
	line, err := json.Marshal(map[string]any{
		"at":   time.Now().Format("15:04:05.000"),
		"kind": kind,
		"data": data,
	})
	if err != nil {
		log.Printf("[журнал] не удалось сериализовать запись: %v", err)
		return
	}

	j.mu.Lock()
	defer j.mu.Unlock()

	file, err := os.OpenFile(j.Path, os.O_APPEND|os.O_CREATE|os.O_WRONLY, 0o644)
	if err != nil {
		log.Printf("[журнал] не удалось открыть файл: %v", err)
		return
	}
	defer file.Close()

	_, _ = file.Write(append(line, '\n'))
}
