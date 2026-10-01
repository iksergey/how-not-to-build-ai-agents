/// Владелец фактов и состояния.
///
/// Косяк 1: у факта есть владелец. Дедлайны живут здесь, модель их не знает
/// и не должна знать, она спрашивает инструмент.
///
/// Косяк 5: один владелец, один путь записи. Всё, что меняет состояние,
/// проходит через mutate под блокировкой и сразу сохраняется на диск.
/// Читать можно параллельно, записывать по очереди.
package agentcontour.data;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Consumer;

public final class CourseDb {

    // Факты. Модуля M-03 здесь нарочно нет: это тот самый несуществующий дедлайн
    private static final Map<String, LocalDate> DEADLINES = new TreeMap<>(Map.of(
            "M-01", LocalDate.of(2026, 10, 15),
            "M-02", LocalDate.of(2026, 11, 1)));

    private static final ObjectMapper JSON = new ObjectMapper();

    public static final class State {
        public List<String> reminders = new ArrayList<>();
    }

    private final Path statePath;
    private final Object gate = new Object();
    private State state;

    public CourseDb(Path statePath) throws IOException {
        this.statePath = statePath;
        this.state = Files.exists(statePath)
                ? JSON.readValue(Files.readString(statePath), State.class)
                : new State();
    }

    public List<String> moduleIds() {
        return List.copyOf(DEADLINES.keySet());
    }

    public Optional<LocalDate> deadline(String moduleId) {
        return Optional.ofNullable(DEADLINES.get(moduleId.strip().toUpperCase()));
    }

    public List<String> reminders() {
        synchronized (gate) {
            return List.copyOf(state.reminders);
        }
    }

    public void addReminder(String text) {
        mutate(s -> s.reminders.add(text));
    }

    // Единственный путь записи: взять блокировку, изменить, сохранить
    private void mutate(Consumer<State> change) {
        synchronized (gate) {
            change.accept(state);
            try {
                Files.writeString(statePath, JSON.writerWithDefaultPrettyPrinter().writeValueAsString(state));
            } catch (IOException error) {
                throw new UncheckedIOException(error);
            }
        }
    }
}
