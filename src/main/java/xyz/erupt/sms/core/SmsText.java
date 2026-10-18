package xyz.erupt.sms.core;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The two text shapes forms use: one value per line, and {@code key=value} per line.
 *
 * @author YuePeng
 */
public final class SmsText {

    private SmsText() {
    }

    public static List<String> lines(String text) {
        return null == text ? List.of() : text.lines().map(String::trim).filter(it -> !it.isEmpty()).toList();
    }

    public static LinkedHashMap<String, String> pairs(String text) {
        LinkedHashMap<String, String> map = new LinkedHashMap<>();
        for (String line : lines(text)) {
            int eq = line.indexOf('=');
            if (eq > 0) map.put(line.substring(0, eq).trim(), line.substring(eq + 1).trim());
        }
        return map;
    }

    // "${code}" placeholders filled in, for the record of what was sent
    public static String render(String content, Map<String, String> params) {
        if (null == content) return null;
        String rendered = content;
        for (Map.Entry<String, String> entry : params.entrySet()) rendered = rendered.replace("${" + entry.getKey() + "}", entry.getValue());
        return rendered;
    }

}
