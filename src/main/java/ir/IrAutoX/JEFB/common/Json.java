package ir.IrAutoX.JEFB.common;

import java.util.*;

/**
 * Minimal, dependency-free JSON parser and writer.
 *
 * Supports: objects, arrays, strings (with escapes), numbers (long/double),
 * booleans, null. Output is deterministic (LinkedHashMap ordering preserved)
 * which keeps generated files stable for reproducible builds.
 */
public final class Json {

    /** Parse a JSON document into Map / List / String / Long / Double / Boolean / null. */
    public static Object parse(String text) {
        Parser p = new Parser(text);
        Object v = p.parseValue();
        p.skipWs();
        if (!p.atEnd()) throw p.error("Trailing content after JSON value");
        return v;
    }

    /** Serialize any Map/List/String/Number/Boolean/null into compact JSON. */
    public static String write(Object value) {
        StringBuilder sb = new StringBuilder(256);
        writeValue(sb, value);
        return sb.toString();
    }

    /** Serialize with 2-space indentation for human-readable files. */
    public static String writePretty(Object value) {
        StringBuilder sb = new StringBuilder(256);
        writeValue(sb, value, 0);
        return sb.toString();
    }

    // ------------------------------------------------------------------ write

    private static void writeValue(StringBuilder sb, Object v, int indent) {
        if (v == null) { sb.append("null"); return; }
        if (v instanceof String s) { writeString(sb, s); return; }
        if (v instanceof Boolean || v instanceof Integer || v instanceof Long) {
            sb.append(v); return;
        }
        if (v instanceof Double d) {
            if (d.isNaN() || d.isInfinite()) sb.append("null");
            else sb.append(d);
            return;
        }
        if (v instanceof Number n) { sb.append(n); return; }
        if (v instanceof Map<?, ?> m) { writeMap(sb, m, indent); return; }
        if (v instanceof Iterable<?> it) { writeArray(sb, it, indent); return; }
        if (v instanceof Object[] arr) { writeArray(sb, Arrays.asList(arr), indent); return; }
        writeString(sb, v.toString());
    }

    private static void writeMap(StringBuilder sb, Map<?, ?> m, int indent) {
        if (m.isEmpty()) { sb.append("{}"); return; }
        sb.append("{\n");
        int i = 0;
        for (Map.Entry<?, ?> e : m.entrySet()) {
            indent(sb, indent + 1);
            writeString(sb, String.valueOf(e.getKey()));
            sb.append(": ");
            writeValue(sb, e.getValue(), indent + 1);
            if (++i < m.size()) sb.append(",");
            sb.append("\n");
        }
        indent(sb, indent);
        sb.append("}");
    }

    private static void writeArray(StringBuilder sb, Iterable<?> it, int indent) {
        Iterator<?> iter = it.iterator();
        if (!iter.hasNext()) { sb.append("[]"); return; }
        sb.append("[\n");
        int i = 0, size = (it instanceof Collection<?> c) ? c.size() : -1;
        while (iter.hasNext()) {
            indent(sb, indent + 1);
            writeValue(sb, iter.next(), indent + 1);
            if (++i != size) sb.append(",");
            sb.append("\n");
        }
        indent(sb, indent);
        sb.append("]");
    }

    private static void indent(StringBuilder sb, int level) {
        for (int i = 0; i < level; i++) sb.append("  ");
    }

    private static void writeString(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"'  -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        sb.append('"');
    }

    // ------------------------------------------------------------------ parse

    private static final class Parser {
        private final String s;
        private int pos;

        Parser(String s) {
            this.s = Objects.requireNonNull(s, "json text");
        }

        boolean atEnd() { return pos >= s.length(); }

        JsonException error(String msg) {
            int line = 1, col = 1;
            for (int i = 0; i < pos && i < s.length(); i++) {
                if (s.charAt(i) == '\n') { line++; col = 1; } else col++;
            }
            return new JsonException(msg + " at line " + line + ", column " + col);
        }

        void skipWs() {
            while (pos < s.length()) {
                char c = s.charAt(pos);
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') pos++;
                else if (c == '/' && pos + 1 < s.length() && s.charAt(pos + 1) == '/') {
                    while (pos < s.length() && s.charAt(pos) != '\n') pos++;   // tolerate // comments
                } else break;
            }
        }

        char peek() {
            if (atEnd()) throw error("Unexpected end of input");
            return s.charAt(pos);
        }

        Object parseValue() {
            skipWs();
            char c = peek();
            return switch (c) {
                case '{' -> parseObject();
                case '[' -> parseArray();
                case '"' -> parseString();
                case 't' -> parseKeyword("true", Boolean.TRUE);
                case 'f' -> parseKeyword("false", Boolean.FALSE);
                case 'n' -> parseKeyword("null", null);
                default -> parseNumber();
            };
        }

        Object parseKeyword(String word, Object value) {
            if (s.startsWith(word, pos)) { pos += word.length(); return value; }
            throw error("Invalid token, expected '" + word + "'");
        }

        Map<String, Object> parseObject() {
            pos++; // {
            Map<String, Object> map = new LinkedHashMap<>();
            skipWs();
            if (!atEnd() && peek() == '}') { pos++; return map; }
            while (true) {
                skipWs();
                if (atEnd() || peek() != '"') throw error("Expected string key in object");
                String key = parseString();
                skipWs();
                if (atEnd() || peek() != ':') throw error("Expected ':' after object key");
                pos++;
                map.put(key, parseValue());
                skipWs();
                if (atEnd()) throw error("Unterminated object");
                char c = peek();
                if (c == ',') { pos++; continue; }
                if (c == '}') { pos++; return map; }
                throw error("Expected ',' or '}' in object, got '" + c + "'");
            }
        }

        List<Object> parseArray() {
            pos++; // [
            List<Object> list = new ArrayList<>();
            skipWs();
            if (!atEnd() && peek() == ']') { pos++; return list; }
            while (true) {
                list.add(parseValue());
                skipWs();
                if (atEnd()) throw error("Unterminated array");
                char c = peek();
                if (c == ',') { pos++; continue; }
                if (c == ']') { pos++; return list; }
                throw error("Expected ',' or ']' in array, got '" + c + "'");
            }
        }

        String parseString() {
            pos++; // opening quote
            StringBuilder sb = new StringBuilder();
            while (true) {
                if (atEnd()) throw error("Unterminated string");
                char c = s.charAt(pos++);
                if (c == '"') return sb.toString();
                if (c == '\\') {
                    if (atEnd()) throw error("Unterminated escape");
                    char e = s.charAt(pos++);
                    switch (e) {
                        case '"'  -> sb.append('"');
                        case '\\' -> sb.append('\\');
                        case '/'  -> sb.append('/');
                        case 'n'  -> sb.append('\n');
                        case 'r'  -> sb.append('\r');
                        case 't'  -> sb.append('\t');
                        case 'b'  -> sb.append('\b');
                        case 'f'  -> sb.append('\f');
                        case 'u' -> {
                            if (pos + 4 > s.length()) throw error("Bad unicode escape");
                            sb.append((char) Integer.parseInt(s.substring(pos, pos + 4), 16));
                            pos += 4;
                        }
                        default -> throw error("Unknown escape '\\" + e + "'");
                    }
                } else sb.append(c);
            }
        }

        Object parseNumber() {
            int start = pos;
            if (!atEnd() && (peek() == '-' || peek() == '+')) pos++;
            boolean isDouble = false;
            while (!atEnd()) {
                char c = s.charAt(pos);
                if (c >= '0' && c <= '9') pos++;
                else if (c == '.' || c == 'e' || c == 'E' || c == '-' || c == '+') {
                    isDouble = isDouble || c == '.' || c == 'e' || c == 'E';
                    pos++;
                } else break;
            }
            if (pos == start) throw error("Invalid number");
            String num = s.substring(start, pos);
            try {
                return isDouble ? (Object) Double.parseDouble(num) : (Object) Long.parseLong(num);
            } catch (NumberFormatException e) {
                throw error("Invalid number '" + num + "'");
            }
        }
    }

    /** Raised for malformed JSON with position info. */
    public static class JsonException extends RuntimeException {
        public JsonException(String message) { super(message); }
    }

    // Convenience typed accessors -------------------------------------------

    @SuppressWarnings("unchecked")
    public static Map<String, Object> asMap(Object o) {
        return (o instanceof Map<?, ?> m) ? (Map<String, Object>) m : Collections.emptyMap();
    }

    @SuppressWarnings("unchecked")
    public static List<Object> asList(Object o) {
        return (o instanceof List<?> l) ? (List<Object>) l : Collections.emptyList();
    }

    public static String str(Object o, String def) {
        return (o instanceof String s) ? s : def;
    }

    public static boolean bool(Object o, boolean def) {
        return (o instanceof Boolean b) ? b : def;
    }

    public static long lng(Object o, long def) {
        if (o instanceof Number n) return n.longValue();
        return def;
    }

    private Json() {}
}
