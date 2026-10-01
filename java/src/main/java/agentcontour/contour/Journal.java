/// Журнал — что система сделала на самом деле. Не стенограмма разговора,
/// а запись действий: каждый вызов модели, каждый вызов инструмента с
/// аргументами и результатом, каждый отказ. Одна строка JSON на событие,
/// файл можно читать глазами и grep'ом.
///
/// Правило из доклада: сделано только то, что есть в журнале.
package agentcontour.contour;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

public final class Journal {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private final Path path;
    // Один писатель за раз: журнал тоже состояние, и у него один путь записи
    private final Object gate = new Object();

    public Journal(Path path) {
        this.path = path;
    }

    public Path path() {
        return path;
    }

    public void write(String kind, Map<String, ?> data) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("at", LocalTime.now().format(TIME));
        entry.put("kind", kind);
        entry.put("data", data);

        try {
            String line = JSON.writeValueAsString(entry) + System.lineSeparator();
            synchronized (gate) {
                Files.writeString(path, line, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            }
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
    }
}
