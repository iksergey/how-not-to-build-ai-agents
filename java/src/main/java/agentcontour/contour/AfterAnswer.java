/// [4] После ответа: проверка до того, как текст увидит человек.
///
/// Запрет проверяет код (косяк 2). Инструкция может попросить модель не выдавать
/// секреты, но держит только проверка снаружи: что бы модель ни согласилась
/// «дописать», наружу это не уйдёт. Нашли секрет — заменяем ответ целиком:
/// вырезать «только его» ненадёжно, секрет мог принять неожиданную форму.
package agentcontour.contour;

import java.util.Map;
import java.util.regex.Pattern;

public final class AfterAnswer {

    // Промокоды школы выглядят так: SHKOLA-50, SHKOLA-100
    private static final Pattern PROMO_CODE = Pattern.compile("\\bSHKOLA-\\d{2,3}\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern CARD_NUMBER = Pattern.compile("\\b\\d{4}[ -]?\\d{4}[ -]?\\d{4}[ -]?\\d{4}\\b");
    private static final Pattern THINK_BLOCK = Pattern.compile("<think>.*?</think>", Pattern.DOTALL);

    private final Journal journal;

    public AfterAnswer(Journal journal) {
        this.journal = journal;
    }

    public String check(String text) {
        // Модели с рассуждениями иногда отдают их в самом тексте
        text = THINK_BLOCK.matcher(text == null ? "" : text).replaceAll("").strip();

        if (text.isEmpty()) {
            journal.write("answer.empty", Map.of());
            return "Ответа не получилось. Попробуйте переформулировать вопрос.";
        }

        String kind = findSecret(text);
        if (kind != null) {
            IO.println("[4 выход] в ответе " + kind + " — ответ скрыт целиком");
            journal.write("answer.blocked", Map.of("reason", kind));

            return "[Ответ скрыт: в нём оказались служебные данные школы.]";
        }

        IO.println("[4 выход] ответ чист");
        journal.write("answer", Map.of("text", text));

        return text;
    }

    private static String findSecret(String text) {
        if (PROMO_CODE.matcher(text).find()) {
            return "промокод";
        }
        if (CARD_NUMBER.matcher(text).find()) {
            return "номер карты";
        }
        return null;
    }
}
