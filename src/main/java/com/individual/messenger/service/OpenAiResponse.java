package com.individual.messenger.service;

import com.openai.models.responses.Response;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Iterator;

public final class OpenAiResponse {
    private final Response raw;

    private OpenAiResponse(Response raw) {
        this.raw = raw;
    }

    public static OpenAiResponse from(Response raw) {
        return new OpenAiResponse(raw);
    }

    /** SDK에 outputText()가 있으면 그걸 쓰고, 없으면 output/content/text를 훑어서 텍스트를 뽑는다 */
    public String outputText() {
        // 1) 혹시 SDK에 outputText()가 있으면 그대로 호출
        String direct = tryInvokeString(raw, "outputText");
        if (direct != null) return direct;

        // 2) 없으면 output()/outputs() -> content() -> text() 구조를 리플렉션으로 추출
        Object outputs = tryInvoke(raw, "output");
        if (outputs == null) outputs = tryInvoke(raw, "outputs");
        if (!(outputs instanceof Collection<?> outCol)) return "";

        StringBuilder sb = new StringBuilder();
        for (Object outItem : outCol) {
            Object content = tryInvoke(outItem, "content");
            if (!(content instanceof Collection<?> contentCol)) continue;

            for (Object c : contentCol) {
                String t = tryInvokeString(c, "text");
                if (t == null) t = tryInvokeString(c, "getText");
                if (t != null && !t.isBlank()) {
                    if (!sb.isEmpty()) sb.append("\n");
                    sb.append(t);
                }
            }
        }
        return sb.toString();
    }

    // --- reflection helpers ---
    private static Object tryInvoke(Object target, String methodName) {
        try {
            Method m = target.getClass().getMethod(methodName);
            return m.invoke(target);
        } catch (Exception ignore) {
            return null;
        }
    }

    private static String tryInvokeString(Object target, String methodName) {
        Object v = tryInvoke(target, methodName);
        return (v instanceof String s) ? s : null;
    }

    public Response raw() {
        return raw;
    }
}
