/// Контур — ваш код вокруг модели (слайд 7: модель думает, контур решает).
/// Четыре точки вставки собраны в одну запись, чтобы цикл в Agent.java читался.
package agentcontour.contour;

public record Contour(
        BeforeModel before,
        AroundModel aroundModel,
        AroundTool aroundTool,
        AfterAnswer after) {

    static String shortText(String text, int limit) {
        if (text == null) {
            return "";
        }
        String line = text.replace("\n", " ");
        return line.length() > limit ? line.substring(0, limit) + "…" : line;
    }
}
