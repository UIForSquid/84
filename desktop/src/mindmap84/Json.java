package mindmap84;

import java.util.*;

/**
 * Tiny self-contained JSON reader/writer (no external dependencies).
 * Parses into: Map<String,Object>, List<Object>, String, Double, Boolean, null.
 * Writer takes the same shape. Enough for this app's save files.
 */
final class Json {

    // ---------- writing ----------
    static String write(Object o) {
        StringBuilder sb = new StringBuilder();
        writeVal(o, sb);
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static void writeVal(Object o, StringBuilder sb) {
        if (o == null) { sb.append("null"); return; }
        if (o instanceof String) { writeStr((String) o, sb); return; }
        if (o instanceof Boolean) { sb.append(o.toString()); return; }
        if (o instanceof Number) {
            double d = ((Number) o).doubleValue();
            if (d == Math.floor(d) && !Double.isInfinite(d)) sb.append(Long.toString((long) d));
            else sb.append(Double.toString(d));
            return;
        }
        if (o instanceof Map) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<String, Object> e : ((Map<String, Object>) o).entrySet()) {
                if (!first) sb.append(',');
                first = false;
                writeStr(e.getKey(), sb);
                sb.append(':');
                writeVal(e.getValue(), sb);
            }
            sb.append('}');
            return;
        }
        if (o instanceof List) {
            sb.append('[');
            boolean first = true;
            for (Object v : (List<Object>) o) {
                if (!first) sb.append(',');
                first = false;
                writeVal(v, sb);
            }
            sb.append(']');
            return;
        }
        writeStr(o.toString(), sb);
    }

    private static void writeStr(String s, StringBuilder sb) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':  sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n");  break;
                case '\r': sb.append("\\r");  break;
                case '\t': sb.append("\\t");  break;
                default:
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
            }
        }
        sb.append('"');
    }

    // ---------- parsing ----------
    static Object parse(String s) {
        return new P(s).parseTop();
    }

    private static final class P {
        private final String s;
        private int i;
        P(String s) { this.s = s; }

        Object parseTop() {
            ws();
            Object v = val();
            ws();
            return v;
        }

        private Object val() {
            ws();
            char c = peek();
            switch (c) {
                case '{': return obj();
                case '[': return arr();
                case '"': return str();
                case 't': expect("true");  return Boolean.TRUE;
                case 'f': expect("false"); return Boolean.FALSE;
                case 'n': expect("null");  return null;
                default:  return num();
            }
        }

        private Map<String, Object> obj() {
            Map<String, Object> m = new LinkedHashMap<>();
            i++; // {
            ws();
            if (peek() == '}') { i++; return m; }
            while (true) {
                ws();
                String k = str();
                ws();
                if (next() != ':') throw err("expected :");
                Object v = val();
                m.put(k, v);
                ws();
                char c = next();
                if (c == ',') continue;
                if (c == '}') break;
                throw err("expected , or }");
            }
            return m;
        }

        private List<Object> arr() {
            List<Object> l = new ArrayList<>();
            i++; // [
            ws();
            if (peek() == ']') { i++; return l; }
            while (true) {
                l.add(val());
                ws();
                char c = next();
                if (c == ',') continue;
                if (c == ']') break;
                throw err("expected , or ]");
            }
            return l;
        }

        private String str() {
            if (next() != '"') throw err("expected string");
            StringBuilder sb = new StringBuilder();
            while (true) {
                char c = next();
                if (c == '"') break;
                if (c == '\\') {
                    char e = next();
                    switch (e) {
                        case '"':  sb.append('"');  break;
                        case '\\': sb.append('\\'); break;
                        case '/':  sb.append('/');  break;
                        case 'n':  sb.append('\n'); break;
                        case 'r':  sb.append('\r'); break;
                        case 't':  sb.append('\t'); break;
                        case 'b':  sb.append('\b'); break;
                        case 'f':  sb.append('\f'); break;
                        case 'u':
                            String hex = s.substring(i, i + 4);
                            i += 4;
                            sb.append((char) Integer.parseInt(hex, 16));
                            break;
                        default: sb.append(e);
                    }
                } else {
                    sb.append(c);
                }
            }
            return sb.toString();
        }

        private Double num() {
            int start = i;
            while (i < s.length()) {
                char c = s.charAt(i);
                if (c == '-' || c == '+' || c == '.' || c == 'e' || c == 'E' || (c >= '0' && c <= '9')) i++;
                else break;
            }
            return Double.parseDouble(s.substring(start, i));
        }

        private void expect(String word) {
            if (!s.startsWith(word, i)) throw err("expected " + word);
            i += word.length();
        }

        private void ws() { while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++; }
        private char peek() { if (i >= s.length()) throw err("unexpected end"); return s.charAt(i); }
        private char next() { if (i >= s.length()) throw err("unexpected end"); return s.charAt(i++); }
        private RuntimeException err(String m) { return new RuntimeException("JSON parse: " + m + " at " + i); }
    }

    // ---------- typed helpers ----------
    @SuppressWarnings("unchecked")
    static Map<String, Object> asMap(Object o) { return o instanceof Map ? (Map<String, Object>) o : null; }
    @SuppressWarnings("unchecked")
    static List<Object> asList(Object o) { return o instanceof List ? (List<Object>) o : null; }
    static String asStr(Object o) { return o == null ? null : o.toString(); }
    static double asNum(Object o, double dflt) { return o instanceof Number ? ((Number) o).doubleValue() : dflt; }
    static boolean asBool(Object o) { return o instanceof Boolean && (Boolean) o; }

    private Json() {}
}
