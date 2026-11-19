import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Sampel 08: parser JSON mini tanpa dependensi.
 *
 * Parser ini hanya mengenal tipe dasar yang dipakai berkas konfigurasi
 * profil ECU: objek {..}, larik [..], string "...", bilangan bulat,
 * boolean, dan null. Bilangan pecahan sengaja tidak didukung agar
 * perilakunya mudah dipahami. Jika JSON rusak, parser melempar
 * JsonException dengan posisi dan pesan yang jelas -- bukan diam-diam
 * mengembalikan null.
 *
 * Jalankan dari direktori sampel ini:
 *   javac -d out src/Main.java
 *   java -cp out Main
 */
public class Main {

    // ===== Exception khusus =====
    /** Kesalahan sintaks JSON. Pesannya harus bisa dibaca pengguna. */
    static class JsonException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        JsonException(String message) {
            super(message);
        }
    }

    // ============================================================
    // PARSER
    // ============================================================
    static class Parser {
        private final String text;
        private int pos = 0;

        Parser(String text) {
            this.text = text;
        }

        Object parseDocument() {
            skipWhitespace();
            Object value = parseValue();
            skipWhitespace();
            if (pos < text.length()) {
                throw error("ada karakter tersisa setelah nilai JSON");
            }
            return value;
        }

        private Object parseValue() {
            if (pos >= text.length()) throw error("akhir teks sebelum nilai selesai");
            char c = text.charAt(pos);
            if (c == '{') return parseObject();
            if (c == '[') return parseArray();
            if (c == '"') return parseString();
            if (c == '-' || (c >= '0' && c <= '9')) return parseInteger();
            if (text.startsWith("true", pos)) { pos += 4; return Boolean.TRUE; }
            if (text.startsWith("false", pos)) { pos += 5; return Boolean.FALSE; }
            if (text.startsWith("null", pos)) { pos += 4; return null; }
            throw error("token tidak dikenal: '" + c + "'");
        }

        private Map<String, Object> parseObject() {
            Map<String, Object> map = new LinkedHashMap<>(); // urutan kunci dipertahankan
            pos++; // lewati '{'
            skipWhitespace();
            if (peek() == '}') { pos++; return map; }
            while (true) {
                skipWhitespace();
                if (peek() != '"') throw error("kunci objek harus string");
                String key = parseString();
                skipWhitespace();
                expect(':');
                skipWhitespace();
                map.put(key, parseValue());
                skipWhitespace();
                char c = peek();
                if (c == ',') { pos++; continue; }
                if (c == '}') { pos++; return map; }
                throw error("diharapkan ',' atau '}' dalam objek");
            }
        }

        private List<Object> parseArray() {
            List<Object> list = new ArrayList<>();
            pos++; // lewati '['
            skipWhitespace();
            if (peek() == ']') { pos++; return list; }
            while (true) {
                skipWhitespace();
                list.add(parseValue());
                skipWhitespace();
                char c = peek();
                if (c == ',') { pos++; continue; }
                if (c == ']') { pos++; return list; }
                throw error("diharapkan ',' atau ']' dalam larik");
            }
        }

        private String parseString() {
            pos++; // lewati '"'
            StringBuilder sb = new StringBuilder();
            while (true) {
                if (pos >= text.length()) throw error("string tidak ditutup");
                char c = text.charAt(pos++);
                if (c == '"') return sb.toString();
                if (c == '\\') {
                    if (pos >= text.length()) throw error("escape terpotong");
                    char e = text.charAt(pos++);
                    switch (e) {
                        case '"': sb.append('"'); break;
                        case '\\': sb.append('\\'); break;
                        case '/': sb.append('/'); break;
                        case 'n': sb.append('\n'); break;
                        case 't': sb.append('\t'); break;
                        case 'u':
                            if (pos + 4 > text.length()) throw error("escape \\u tidak lengkap");
                            try {
                                sb.append((char) Integer.parseInt(text.substring(pos, pos + 4), 16));
                            } catch (NumberFormatException ex) {
                                throw error("escape \\u bukan hex");
                            }
                            pos += 4;
                            break;
                        default: throw error("escape tidak dikenal: \\" + e);
                    }
                    continue;
                }
                if (c < 0x20) throw error("kontrol karakter di dalam string");
                sb.append(c);
            }
        }

        private Long parseInteger() {
            int start = pos;
            if (peek() == '-') pos++;
            int digitsStart = pos;
            while (pos < text.length() && Character.isDigit(text.charAt(pos))) pos++;
            if (pos == digitsStart) throw error("angka tanpa digit");
            try {
                return Long.parseLong(text.substring(start, pos));
            } catch (NumberFormatException ex) {
                throw error("bilangan bulat di luar jangkauan long");
            }
        }

        private void skipWhitespace() {
            while (pos < text.length()) {
                char c = text.charAt(pos);
                if (c == ' ' || c == '\n' || c == '\r' || c == '\t') pos++;
                else break;
            }
        }

        private void expect(char wanted) {
            if (peek() != wanted) throw error("diharapkan '" + wanted + "'");
            pos++;
        }

        private char peek() {
            if (pos >= text.length()) throw error("teks berakhir terlalu awal");
            return text.charAt(pos);
        }

        private JsonException error(String message) {
            int line = 1, col = 1;
            for (int i = 0; i < Math.min(pos, text.length()); i++) {
                if (text.charAt(i) == '\n') { line++; col = 1; } else col++;
            }
            return new JsonException(message + " (baris " + line + ", kolom " + col + ")");
        }
    }

    // ============================================================
    // PROGRAM UTAMA
    // ============================================================
    static int pass = 0, fail = 0;

    public static void main(String[] args) throws IOException {
        System.out.println("== Sampel 08: parser JSON mini ==");

        // 1) Muat berkas konfigurasi profil sintetis.
        String text = Files.readString(Path.of("data", "profile.json"), StandardCharsets.UTF_8);
        Object doc = new Parser(text).parseDocument();
        System.out.println("\n-- Struktur hasil parse --");
        printValue(doc, 0);

        // 2) Tampilkan field yang relevan.
        @SuppressWarnings("unchecked")
        Map<String, Object> profile = (Map<String, Object>) doc;
        System.out.println("\n-- Ringkasan profil --");
        System.out.println("family          : " + profile.get("family"));
        System.out.println("identityRequests: " + profile.get("identityRequests"));
        System.out.println("dtcReadRequests : " + profile.get("dtcReadRequests"));

        // 3) Pengujian mandiri.
        System.out.println("\n-- Pengujian --");
        testValidJson(profile);
        testMalformedMissingComma();
        testMalformedUnclosedString();
        testMalformedTrailingComma();

        System.out.println("\nRingkasan: PASS=" + pass + " FAIL=" + fail);
        if (fail > 0) {
            System.out.println("HASIL: GAGAL");
            System.exit(1);
        }
        System.out.println("HASIL: OK");
    }

    // Mencetak nilai JSON secara berjenjang agar mudah dibaca pemula.
    static void printValue(Object v, int depth) {
        String pad = "  ".repeat(depth);
        if (v instanceof Map<?, ?> m) {
            for (Map.Entry<?, ?> e : m.entrySet()) {
                Object child = e.getValue();
                if (child instanceof Map || child instanceof List) {
                    System.out.println(pad + e.getKey() + ":");
                    printValue(child, depth + 1);
                } else {
                    System.out.println(pad + e.getKey() + ": " + formatScalar(child));
                }
            }
        } else if (v instanceof List<?> l) {
            if (l.isEmpty()) { System.out.println(pad + "[]"); return; }
            for (Object item : l) {
                if (item instanceof Map || item instanceof List) {
                    System.out.println(pad + "-");
                    printValue(item, depth + 1);
                } else {
                    System.out.println(pad + "- " + formatScalar(item));
                }
            }
        } else {
            System.out.println(pad + formatScalar(v));
        }
    }

    static String formatScalar(Object v) {
        if (v == null) return "null";
        if (v instanceof String s) return "\"" + s + "\"";
        return v.toString();
    }

    static void testValidJson(Map<String, Object> profile) {
        check("parse valid: family terbaca", "contoh-baseline".equals(profile.get("family")));
        @SuppressWarnings("unchecked")
        List<Object> read = (List<Object>) profile.get("dtcReadRequests");
        check("parse valid: 3 query baca DTC", read != null && read.size() == 3);
        check("parse valid: query pertama 72 05 74 01 14",
                read != null && "72 05 74 01 14".equals(read.get(0)));
    }

    static void testMalformedMissingComma() {
        String bad = "{ \"a\": 1 \"b\": 2 }";
        String msg = expectFailure(bad);
        if (msg != null) System.out.println("   pesan  : " + msg);
        check("JSON rusak (koma hilang) ditolak dengan pesan", msg != null && !msg.isBlank());
    }

    static void testMalformedUnclosedString() {
        String msg = expectFailure("[ \"belum ditutup ]");
        if (msg != null) System.out.println("   pesan  : " + msg);
        check("JSON rusak (string tak tertutup) ditolak", msg != null && msg.contains("string"));
    }

    static void testMalformedTrailingComma() {
        String msg = expectFailure("[1, 2,]");
        if (msg != null) System.out.println("   pesan  : " + msg);
        check("JSON rusak (koma akhir) ditolak", msg != null);
    }

    /** Mengembalikan pesan error, atau null jika justru berhasil di-parse. */
    static String expectFailure(String json) {
        try {
            new Parser(json).parseDocument();
            return null;
        } catch (JsonException e) {
            return e.getMessage();
        }
    }

    static void check(String label, boolean ok) {
        if (ok) pass++; else fail++;
        System.out.println((ok ? "PASS" : "FAIL") + " - " + label);
    }
}
