package com.li.community.common.utils;

import java.util.*;

public class DfaFilterUtil {

    public static final String ACTION_REJECT = "REJECT";
    public static final String ACTION_REPLACE = "REPLACE";

    /**
     * copy-on-write：构建完整新树后 volatile 引用整体替换。
     * 读侧无锁遍历旧树，不会读到重建中的半棵树（过滤失效窗口）。
     * 词尾标记 '\0' 映射处置动作 (REJECT / REPLACE)。
     */
    private volatile Map<Character, Object> rootNode = new HashMap<>();

    public DfaFilterUtil() {
    }

    public void buildTree(Collection<String> words) {
        Map<String, String> wordActions = new HashMap<>();
        if (words != null) {
            words.forEach(w -> wordActions.put(w, ACTION_REJECT));
        }
        buildTree(wordActions);
    }

    public void buildTree(Map<String, String> wordActions) {
        Map<Character, Object> newRoot = new HashMap<>();
        if (wordActions != null) {
            for (Map.Entry<String, String> entry : wordActions.entrySet()) {
                String word = entry.getKey();
                String action = entry.getValue() != null ? entry.getValue() : ACTION_REJECT;
                if (word == null || word.trim().isEmpty()) continue;
                word = word.trim();
                Map<Character, Object> currentMap = newRoot;
                for (int i = 0; i < word.length(); i++) {
                    char c = word.charAt(i);
                    @SuppressWarnings("unchecked")
                    Map<Character, Object> subMap = (Map<Character, Object>) currentMap.get(c);
                    if (subMap == null) {
                        subMap = new HashMap<>();
                        currentMap.put(c, subMap);
                    }
                    currentMap = subMap;
                    if (i == word.length() - 1) {
                        currentMap.put('\0', action); // 词尾结束标记 -> 处置动作
                    }
                }
            }
        }
        this.rootNode = newRoot;
    }

    /** 文本命中任意敏感词 (不论 REJECT / REPLACE) */
    public boolean contains(String text) {
        if (text == null || text.isEmpty()) return false;
        for (int i = 0; i < text.length(); i++) {
            if (matchAction(text, i)[0] != null) {
                return true;
            }
        }
        return false;
    }

    /** 文本命中需直接拒绝的敏感词 (action = REJECT) */
    public boolean containsReject(String text) {
        if (text == null || text.isEmpty()) return false;
        for (int i = 0; i < text.length(); i++) {
            if (ACTION_REJECT.equals(matchAction(text, i)[0])) {
                return true;
            }
        }
        return false;
    }

    /** 返回全部命中敏感词原文 */
    public Set<String> findSensitiveWords(String text) {
        Set<String> result = new HashSet<>();
        if (text == null || text.isEmpty()) return result;
        for (int i = 0; i < text.length(); i++) {
            Object[] actionAndLength = matchAction(text, i);
            int length = (Integer) actionAndLength[1];
            if (length > 0) {
                result.add(text.substring(i, i + length));
                i = i + length - 1;
            }
        }
        return result;
    }

    /** 返回需直接拒绝 (REJECT) 的命中敏感词原文 */
    public Set<String> findRejectWords(String text) {
        Set<String> result = new HashSet<>();
        if (text == null || text.isEmpty()) return result;
        for (int i = 0; i < text.length(); i++) {
            Object[] actionAndLength = matchAction(text, i);
            int length = (Integer) actionAndLength[1];
            if (ACTION_REJECT.equals(actionAndLength[0]) && length > 0) {
                result.add(text.substring(i, i + length));
                i = i + length - 1;
            }
        }
        return result;
    }

    /** 将命中的敏感词整体替换为指定字符 */
    public String replace(String text, char replaceChar) {
        if (text == null || text.isEmpty()) return text;
        StringBuilder sb = new StringBuilder(text);
        for (int i = 0; i < text.length(); i++) {
            int length = (Integer) matchAction(text, i)[1];
            if (length > 0) {
                for (int j = 0; j < length; j++) {
                    sb.setCharAt(i + j, replaceChar);
                }
                i = i + length - 1;
            }
        }
        return sb.toString();
    }

    /**
     * 返回自 beginIndex 起最长命中词的 [处置动作, 命中长度]；
     * 未命中时为 [null, 0]。纯函数无共享状态，读侧并发安全。
     */
    private Object[] matchAction(String text, int beginIndex) {
        Map<Character, Object> currentMap = rootNode;
        int matchFlag = 0;
        String action = null;
        int length = 0;

        for (int i = beginIndex; i < text.length(); i++) {
            char c = text.charAt(i);
            @SuppressWarnings("unchecked")
            Map<Character, Object> subMap = (Map<Character, Object>) currentMap.get(c);
            if (subMap != null) {
                matchFlag++;
                currentMap = subMap;
                Object tail = currentMap.get('\0');
                if (tail instanceof String tailAction) {
                    action = tailAction;
                    length = matchFlag;
                }
            } else {
                break;
            }
        }
        return new Object[]{action, length};
    }
}
