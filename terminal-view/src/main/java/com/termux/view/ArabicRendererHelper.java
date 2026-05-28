package com.termux.view;

import java.text.Bidi;

public class ArabicRendererHelper {

    public static class LineResult {
        public char[] text;
        public int[] visualToLogicalMap;
    }

    public static boolean hasArabic(char[] text, int start, int count) {
        if (text == null) return false;
        int end = Math.min(start + count, text.length);
        for (int i = start; i < end; i++) {
            char c = text[i];
            if ((c >= 0x0600 && c <= 0x06FF) || (c >= 0x0750 && c <= 0x077F) || 
                (c >= 0xFB50 && c <= 0xFDFF) || (c >= 0xFE70 && c <= 0xFEFF)) {
                return true;
            }
        }
        return false;
    }

    public static char getPrevChar(com.termux.terminal.TerminalBuffer screen, int row) {
        if (row <= 0) return 0;
        com.termux.terminal.TerminalRow prevLineObject = screen.allocateFullLineIfNecessary(screen.externalToInternalRow(row - 1));
        if (prevLineObject != null && prevLineObject.mLineWrap) {
            char[] prevText = prevLineObject.mText;
            for (int j = prevLineObject.getSpaceUsed() - 1; j >= 0; j--) {
                if (!isTashkeel(prevText[j])) {
                    return prevText[j];
                }
            }
        }
        return 0;
    }

    public static char getNextChar(com.termux.terminal.TerminalBuffer screen, int row, com.termux.terminal.TerminalRow lineObject) {
        if (lineObject != null && lineObject.mLineWrap) {
            com.termux.terminal.TerminalRow nextLineObject = screen.allocateFullLineIfNecessary(screen.externalToInternalRow(row + 1));
            if (nextLineObject != null) {
                char[] nextText = nextLineObject.mText;
                int nextSpaceUsed = nextLineObject.getSpaceUsed();
                for (int j = 0; j < nextSpaceUsed; j++) {
                    if (!isTashkeel(nextText[j])) {
                        return nextText[j];
                    }
                }
            }
        }
        return 0;
    }

    public static LineResult reshapeAndReorder(char[] text, int count) {
        return reshapeAndReorder(text, count, (char) 0, (char) 0);
    }

    public static LineResult reshapeAndReorder(char[] text, int count, char prevChar, char nextChar) {
        if (text == null) return null;
        int len = text.length;
        int activeCount = Math.min(count, len);
        if (activeCount <= 0) {
            LineResult result = new LineResult();
            result.text = text.clone();
            result.visualToLogicalMap = new int[len];
            for (int i = 0; i < len; i++) result.visualToLogicalMap[i] = i;
            return result;
        }

        // Shape the Arabic characters
        char[] shaped = new char[activeCount];
        for (int i = 0; i < activeCount; i++) shaped[i] = text[i];
        shapeArabic(shaped, prevChar, nextChar);

        // Convert back to string for Bidi
        String shapedString = new String(shaped);
        LineResult result = reorder(shapedString);

        // Reconstruct the full text array with trailing characters
        char[] fullText = new char[len];
        System.arraycopy(result.text, 0, fullText, 0, result.text.length);
        for (int i = result.text.length; i < len; i++) {
            fullText[i] = text[i];
        }

        int[] fullMap = new int[len];
        System.arraycopy(result.visualToLogicalMap, 0, fullMap, 0, result.visualToLogicalMap.length);
        for (int i = result.visualToLogicalMap.length; i < len; i++) {
            fullMap[i] = i;
        }

        result.text = fullText;
        result.visualToLogicalMap = fullMap;
        return result;
    }

    private static LineResult reorder(String text) {
        int len = text.length();
        char[] visualText = new char[len];
        int[] map = new int[len];
        for (int i = 0; i < len; i++) map[i] = i;

        try {
            Bidi bidi = new Bidi(text, Bidi.DIRECTION_DEFAULT_LEFT_TO_RIGHT);
            if (bidi.isLeftToRight()) {
                for (int i = 0; i < len; i++) visualText[i] = text.charAt(i);
                LineResult result = new LineResult();
                result.text = visualText;
                result.visualToLogicalMap = map;
                return result;
            }

            int count = bidi.getRunCount();
            byte[] levels = new byte[count];
            Integer[] runs = new Integer[count];
            for (int i = 0; i < count; i++) {
                levels[i] = (byte) bidi.getRunLevel(i);
                runs[i] = i;
            }
            Bidi.reorderVisually(levels, 0, runs, 0, count);

            int visualIndex = 0;
            for (int i = 0; i < count; i++) {
                int index = runs[i];
                int runStart = bidi.getRunStart(index);
                int runLimit = bidi.getRunLimit(index);
                int level = bidi.getRunLevel(index);
                if ((level & 1) != 0) { // RTL run
                    for (int j = runLimit - 1; j >= runStart; j--) {
                        visualText[visualIndex] = mirrorChar(text.charAt(j));
                        map[visualIndex] = j;
                        visualIndex++;
                    }
                } else { // LTR run
                    for (int j = runStart; j < runLimit; j++) {
                        visualText[visualIndex] = text.charAt(j);
                        map[visualIndex] = j;
                        visualIndex++;
                    }
                }
            }
            LineResult result = new LineResult();
            result.text = visualText;
            result.visualToLogicalMap = map;
            return result;
        } catch (Exception e) {
            // Fallback
            for (int i = 0; i < len; i++) visualText[i] = text.charAt(i);
            LineResult result = new LineResult();
            result.text = visualText;
            result.visualToLogicalMap = map;
            return result;
        }
    }

    private static char mirrorChar(char c) {
        if (c == '(') return ')';
        if (c == ')') return '(';
        if (c == '[') return ']';
        if (c == ']') return '[';
        if (c == '{') return '}';
        if (c == '}') return '{';
        if (c == '<') return '>';
        if (c == '>') return '<';
        return c;
    }

    private static final char[][] ARABIC_GLYPHS = {
        { '\u0621', '\uFE80', '\uFE80', '\uFE80', '\uFE80', 0 }, // Hamza (no connect)
        { '\u0622', '\uFE81', '\uFE81', '\uFE82', '\uFE82', 1 }, // Alef Madda
        { '\u0623', '\uFE83', '\uFE83', '\uFE84', '\uFE84', 1 }, // Alef Hamza Above
        { '\u0624', '\uFE85', '\uFE85', '\uFE86', '\uFE86', 1 }, // Waw Hamza
        { '\u0625', '\uFE87', '\uFE87', '\uFE88', '\uFE88', 1 }, // Alef Hamza Below
        { '\u0626', '\uFE89', '\uFE8B', '\uFE8C', '\uFE8A', 2 }, // Yeh Hamza
        { '\u0627', '\uFE8D', '\uFE8D', '\uFE8E', '\uFE8E', 1 }, // Alef
        { '\u0628', '\uFE8F', '\uFE91', '\uFE92', '\uFE90', 2 }, // Beh
        { '\u0629', '\uFE93', '\uFE93', '\uFE94', '\uFE94', 1 }, // Teh Marbuta
        { '\u062A', '\uFE95', '\uFE97', '\uFE98', '\uFE96', 2 }, // Teh
        { '\u062B', '\uFE99', '\uFE9B', '\uFE9C', '\uFE9A', 2 }, // Theh
        { '\u062C', '\uFE9D', '\uFE9F', '\uFEA0', '\uFE9E', 2 }, // Jeem
        { '\u062D', '\uFEA1', '\uFEA3', '\uFEA4', '\uFEA2', 2 }, // Hah
        { '\u062E', '\uFEA5', '\uFEA7', '\uFEA8', '\uFEA6', 2 }, // Khah
        { '\u062F', '\uFEA9', '\uFEA9', '\uFEAA', '\uFEAA', 1 }, // Dal
        { '\u0630', '\uFEAB', '\uFEAB', '\uFEAC', '\uFEAC', 1 }, // Thal
        { '\u0631', '\uFEAD', '\uFEAD', '\uFEAE', '\uFEAE', 1 }, // Reh
        { '\u0632', '\uFEAF', '\uFEAF', '\uFEB0', '\uFEB0', 1 }, // Zain
        { '\u0633', '\uFEB1', '\uFEB3', '\uFEB4', '\uFEB2', 2 }, // Seen
        { '\u0634', '\uFEB5', '\uFEB7', '\uFEB8', '\uFEB6', 2 }, // Sheen
        { '\u0635', '\uFEB9', '\uFEBB', '\uFEBC', '\uFEBA', 2 }, // Sad
        { '\u0636', '\uFEBD', '\uFEBF', '\uFEC0', '\uFEBE', 2 }, // Dad
        { '\u0637', '\uFEC1', '\uFEC3', '\uFEC4', '\uFEC2', 2 }, // Tah
        { '\u0638', '\uFEC5', '\uFEC7', '\uFEC8', '\uFEC6', 2 }, // Zah
        { '\u0639', '\uFEC9', '\uFECB', '\uFECC', '\uFECA', 2 }, // Ain
        { '\u063A', '\uFECD', '\uFECF', '\uFED0', '\uFECE', 2 }, // Ghain
        { '\u0641', '\uFED1', '\uFED3', '\uFED4', '\uFED2', 2 }, // Feh
        { '\u0642', '\uFED5', '\uFED7', '\uFED8', '\uFED6', 2 }, // Qaf
        { '\u0643', '\uFED9', '\uFEDB', '\uFEDC', '\uFEDA', 2 }, // Kaf
        { '\u0644', '\uFEDD', '\uFEDF', '\uFEE0', '\uFEDE', 2 }, // Lam
        { '\u0645', '\uFEE1', '\uFEE3', '\uFEE4', '\uFEE2', 2 }, // Meem
        { '\u0646', '\uFEE5', '\uFEE7', '\uFEE8', '\uFEE6', 2 }, // Noon
        { '\u0647', '\uFEE9', '\uFEEB', '\uFEEC', '\uFEEA', 2 }, // Heh
        { '\u0648', '\uFEED', '\uFEED', '\uFEEE', '\uFEEE', 1 }, // Waw
        { '\u0649', '\uFEEF', '\uFEEF', '\uFEF0', '\uFEF0', 1 }, // Alef Maksura
        { '\u064A', '\uFEF1', '\uFEF3', '\uFEF4', '\uFEF2', 2 }, // Yeh
        { '\u067E', '\uFB56', '\uFB58', '\uFB59', '\uFB57', 2 }, // Peh
        { '\u0686', '\uFB7A', '\uFB7C', '\uFB7D', '\uFB7B', 2 }, // Tcheh
        { '\u0698', '\uFB8A', '\uFB8A', '\uFB8B', '\uFB8B', 1 }, // Jeh
        { '\u06AF', '\uFB92', '\uFB94', '\uFB95', '\uFB93', 2 }, // Gaf
        { '\u06CC', '\uFBFC', '\uFBFE', '\uFBFF', '\uFBFD', 2 }, // Farsi Yeh
    };

    private static int getGlyphIndex(char c) {
        for (int i = 0; i < ARABIC_GLYPHS.length; i++) {
            if (ARABIC_GLYPHS[i][0] == c) return i;
        }
        return -1;
    }

    public static boolean isTashkeel(char c) {
        return (c >= 0x064B && c <= 0x065F) || (c == 0x0670);
    }

    private static void shapeArabic(char[] text, char prevChar, char nextChar) {
        for (int i = 0; i < text.length; i++) {
            int gIdx = getGlyphIndex(text[i]);
            if (gIdx == -1) continue;

            boolean connectRight = false;
            boolean connectLeft = false;

            // Check right connectivity (previous logical char)
            boolean foundRight = false;
            for (int j = i - 1; j >= 0; j--) {
                char prev = text[j];
                if (isTashkeel(prev)) continue;
                int prevIdx = getGlyphIndex(prev);
                if (prevIdx != -1) {
                    if (ARABIC_GLYPHS[prevIdx][5] == 2) {
                        connectRight = true;
                    }
                }
                foundRight = true;
                break;
            }
            if (!foundRight && prevChar != 0 && !isTashkeel(prevChar)) {
                int prevIdx = getGlyphIndex(prevChar);
                if (prevIdx != -1) {
                    if (ARABIC_GLYPHS[prevIdx][5] == 2) {
                        connectRight = true;
                    }
                }
            }

            // Check left connectivity (next logical char)
            boolean foundLeft = false;
            for (int j = i + 1; j < text.length; j++) {
                char next = text[j];
                if (isTashkeel(next)) continue;
                int nextIdx = getGlyphIndex(next);
                if (nextIdx != -1) {
                    connectLeft = true;
                }
                foundLeft = true;
                break;
            }
            if (!foundLeft && nextChar != 0 && !isTashkeel(nextChar)) {
                int nextIdx = getGlyphIndex(nextChar);
                if (nextIdx != -1) {
                    connectLeft = true;
                }
            }

            int type = ARABIC_GLYPHS[gIdx][5];
            if (type == 0) { // No connections (Hamza)
                text[i] = ARABIC_GLYPHS[gIdx][1];
            } else if (type == 1) { // Connects right only
                if (connectRight) text[i] = ARABIC_GLYPHS[gIdx][4];
                else text[i] = ARABIC_GLYPHS[gIdx][1];
            } else if (type == 2) { // Connects both sides
                if (connectRight && connectLeft) text[i] = ARABIC_GLYPHS[gIdx][3];
                else if (connectRight) text[i] = ARABIC_GLYPHS[gIdx][4];
                else if (connectLeft) text[i] = ARABIC_GLYPHS[gIdx][2];
                else text[i] = ARABIC_GLYPHS[gIdx][1];
            }
        }
        
        // Handle Lam-Alef ligatures
        for (int i = 0; i < text.length - 1; i++) {
            char c = text[i];
            if (c == '\uFEDD' || c == '\uFEDE') { // Lam Initial or Lam Medial
                // Find next non-tashkeel
                int nextIdx = -1;
                for (int j = i + 1; j < text.length; j++) {
                    if (!isTashkeel(text[j])) {
                        nextIdx = j;
                        break;
                    }
                }
                if (nextIdx != -1) {
                    char next = text[nextIdx];
                    char ligature = 0;
                    if (c == '\uFEDD') { // Lam Initial (no right connection)
                        if (next == '\uFE8E') ligature = '\uFEFB'; // Alef Final -> Lam-Alef Isolated
                        else if (next == '\uFE82') ligature = '\uFEF5'; // Alef Madda Final -> Lam-Alef Madda Isolated
                        else if (next == '\uFE84') ligature = '\uFEF7'; // Alef Hamza Above Final -> Lam-Alef Hamza Above Iso
                        else if (next == '\uFE88') ligature = '\uFEF9'; // Alef Hamza Below Final -> Lam-Alef Hamza Below Iso
                    } else if (c == '\uFEDE') { // Lam Medial (has right connection)
                        if (next == '\uFE8E') ligature = '\uFEFC'; // Alef Final -> Lam-Alef Final
                        else if (next == '\uFE82') ligature = '\uFEF6';
                        else if (next == '\uFE84') ligature = '\uFEF8';
                        else if (next == '\uFE88') ligature = '\uFEFA';
                    }
                    if (ligature != 0) {
                        text[i] = ligature;
                        text[nextIdx] = '\u0020'; // Replace Alef with a space to preserve length and array mapping
                    }
                }
            }
        }
    }
}
