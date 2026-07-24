package com.sushijia.ai.engine;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 提示词模板渲染引擎
 * 将 {{variable}} 替换为实际值
 */
public class PromptEngine {

    private static final Pattern VAR_PATTERN = Pattern.compile("\\{\\{(\\w+)\\}\\}");

    /**
     * 渲染模板
     * @param template 提示词模板正文
     * @param variables 变量键值对
     * @return 渲染后的完整提示词
     */
    public static String render(String template, Map<String, String> variables) {
        if (template == null || variables == null) return template;

        Matcher matcher = VAR_PATTERN.matcher(template);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1);
            String replacement = variables.getOrDefault(key, "{{" + key + "}}");
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * 提取模板中的变量名列表
     */
    public static java.util.List<String> extractVariables(String template) {
        java.util.List<String> vars = new java.util.ArrayList<>();
        Matcher matcher = VAR_PATTERN.matcher(template);
        while (matcher.find()) {
            vars.add(matcher.group(1));
        }
        return vars;
    }
}
