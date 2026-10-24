package com.li.community.common.utils;

import java.util.*;

public class DfaFilterUtil {

    /**
     * copy-on-write：构建完整新树后 volatile 引用整体替换。
     * 读侧无锁遍历旧树，不会读到重建中的半棵树（过滤失效窗口）。
     */
    private volatile Map<Character, Object> rootNode = new HashMap<>();

    public DfaFilterUtil() {
    }

    public void buildTree(Collection<String> words) {
        Map<Character, Object> newRoot = new HashMap<>();
        if (words != null) {
            for (String word : words) {
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
                        currentMap.put('\0', true); // 词尾结束标记
                    }
                }
            }
        }
        this.rootNode = newRoot;
    }

    public boolean contains(String text) {
        if (text == null || text.isEmpty()) return false;
        for (int i = 0; i < text.length(); i++) {
            int length = checkWord(text, i);
            if (length > 0) {
                return true;
            }
        }
        return false;
    }

    public Set<String> findSensitiveWords(String text) {
        Set<String> result = new HashSet<>();
        if (text == null || text.isEmpty()) return result;
        for (int i = 0; i < text.length(); i++) {
            int length = checkWord(text, i);
            if (length > 0) {
                result.add(text.substring(i, i + length));
                i = i + length - 1;
            }
        }
        return result;
    }

    public String replace(String text, char replaceChar) {
        if (text == null || text.isEmpty()) return text;
        StringBuilder sb = new StringBuilder(text);
        for (int i = 0; i < text.length(); i++) {
            int length = checkWord(text, i);
            if (length > 0) {
                for (int j = 0; j < length; j++) {
                    sb.setCharAt(i + j, replaceChar);
                }
                i = i + length - 1;
            }
        }
        return sb.toString();
    }

    private int checkWord(String text, int beginIndex) {
        Map<Character, Object> currentMap = rootNode;
        int matchFlag = 0;
        int wordLength = 0;

        for (int i = beginIndex; i < text.length(); i++) {
            char c = text.charAt(i);
            @SuppressWarnings("unchecked")
            Map<Character, Object> subMap = (Map<Character, Object>) currentMap.get(c);
            if (subMap != null) {
                matchFlag++;
                currentMap = subMap;
                if (currentMap.containsKey('\0')) {
                    wordLength = matchFlag;
                }
            } else {
                break;
            }
        }
        return wordLength;
    }
}
