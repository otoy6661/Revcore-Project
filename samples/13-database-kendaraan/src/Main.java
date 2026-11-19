import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Sampel 13: database kendaraan (sintetis).
 *
 * Membaca data/vehicles.json dengan parser JSON mini milik sampel ini sendiri
 * (pendekatan sama dengan sampel 08, tetapi tidak bergantung pada folder lain),
 * lalu memvalidasi record sebelum dipakai:
 *   - id unik dan tidak kosong,
 *   - displayName tidak kosong,
 *   - family dikenal,
 *   - tidak memuat merek/model kendaraan nyata (daftar tolak sederhana).
 *
 * Semua record adalah data buatan. Tidak ada identitas kendaraan nyata.
 *
 * Jalankan dari direktori sampel ini:
 *   javac -d out src/Main.java
 *   java -cp out Main
 */
public class Main {

    // ============================================================
    // PARSER JSON MINI (mandiri)
    // ============================================================
    static class JsonException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        JsonException(String message) {
            super("JSON tidak valid: " + message);
        }
    }

    static final class Json {
        private final String s;
        private int i = 0;

        Json(String s) {
            this.s = s;
        }

        Object parse() {
            Object v = value();
            ws();
            if (i != s.length()) throw new JsonException("karakter tersisa pada posisi " + i);
            return v;
        }

        private Object value() {
            ws();
            if (i >= s.length()) throw new JsonException("berkas berakhir terlalu awal");
            char c = s.charAt(i);
            switch (c) {
                case '{': return obj();
                case '[': return arr();
                case '"': return str();
                case 't': lit("true"); return Boolean.TRUE;
                case 'f': lit("false"); return Boolean.FALSE;
                case 'n': lit("null"); return null;
                default:
                    if (c == '-' || Character.isDigit(c)) return num();
                    throw new JsonException("karakter '" + c + "' tidak dikenal pada posisi " + i);
            }
        }

        private Map<String, Object> obj() {
            Map<String, Object> m = new LinkedHashMap<>();
            i++; ws();
            if (i < s.length() && s.charAt(i) == '}') { i++; return m; }
            while (true) {
                ws();
                if (i >= s.length() || s.charAt(i) != '"') throw new JsonException("kunci harus string");
                String k = str();
                ws();
                if (i >= s.length() || s.charAt(i) != ':') throw new JsonException("diharapkan ':' setelah kunci '" + k + "'");
                i++;
                if (m.containsKey(k)) throw new JsonException("kunci ganda '" + k + "'");
                m.put(k, value());
                ws();
                if (i >= s.length()) throw new JsonException("objek tidak ditutup");
                char c = s.charAt(i++);
                if (c == ',') continue;
                if (c == '}') return m;
                throw new JsonException("diharapkan ',' atau '}' pada posisi " + (i - 1));
            }
        }

        private List<Object> arr() {
            List<Object> l = new ArrayList<>();
            i++; ws();
            if (i < s.length() && s.charAt(i) == ']') { i++; return l; }
            while (true) {
                l.add(value());
                ws();
                if (i >= s.length()) throw new JsonException("larik tidak ditutup");
                char c = s.charAt(i++);
                if (c == ',') continue;
                if (c == ']') return l;
                throw new JsonException("diharapkan ',' atau ']' pada posisi " + (i - 1));
            }
        }

        private String str() {
            i++;
            StringBuilder sb = new StringBuilder();
            while (i < s.length()) {
                char c = s.charAt(i++);
                if (c == '"') return sb.toString();
                if (c == '\\') {
                    if (i >= s.length()) throw new JsonException("escape terpotong");
                    char e = s.charAt(i++);
                    switch (e) {
                        case '"': case '\\': case '/': sb.append(e); break;
                        case 'n': sb.append('\n'); break;
                        case 't': sb.append('\t'); break;
                        default: throw new JsonException("escape tidak didukung: \\" + e);
                    }
                } else {
                    sb.append(c);
                }
            }
            throw new JsonException("string tidak ditutup");
        }

        private Long num() {
            int start = i;
            if (s.charAt(i) == '-') i++;
            while (i < s.length() && Character.isDigit(s.charAt(i))) i++;
            try {
                return Long.parseLong(s.substring(start, i));
            } catch (NumberFormatException e) {
                throw new JsonException("angka tidak valid: " + s.substring(start, i));
            }
        }

        private void lit(String word) {
            if (!s.startsWith(word, i)) throw new JsonException("literal '" + word + "' tidak cocok");
            i += word.length();
        }

        private void ws() {
            while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
        }
    }

    // ============================================================
    // MODEL DAN VALIDASI
    // ============================================================
    static class DbException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        DbException(String message) {
            super(message);
        }
    }

    record Vehicle(String id, String displayName, String family, String ecuIdPattern, String notes) {}

    /** Family yang dikenal oleh sampel ini (selaras dengan sampel 09). */
    static final List<String> KNOWN_FAMILIES = List.of("contoh-baseline", "contoh-family-lain");

    /**
     * Daftar tolak sederhana untuk merek/model nyata. Bukan daftar lengkap;
     * hanya penjaga agar data contoh tidak tanpa sengaja memuat identitas nyata.
     */
    static final List<String> FORBIDDEN_WORDS = List.of(
            "honda", "yamaha", "suzuki", "kawasaki", "beat", "vario", "supra", "jupiter",
            "mio", "aerox", "nmax", "pcx", "vixion", "cb", "cbr", "revo", "blitz");

    static class VehicleDatabase {
        final Map<String, Vehicle> byId = new LinkedHashMap<>();

        static VehicleDatabase load(Object document) {
            if (!(document instanceof Map<?, ?> root)) throw new DbException("akar JSON harus objek");
            Object arr = root.get("vehicles");
            if (!(arr instanceof List<?> list)) throw new DbException("field 'vehicles' harus larik");
            VehicleDatabase db = new VehicleDatabase();
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> m)) throw new DbException("setiap record harus objek");
                Vehicle v = new Vehicle(
                        req(m, "id"), req(m, "displayName"), req(m, "family"),
                        opt(m, "ecuIdPattern"), opt(m, "notes"));
                db.validate(v);
                db.byId.put(v.id(), v);
            }
            return db;
        }

        private static String req(Map<?, ?> m, String key) {
            Object v = m.get(key);
            if (!(v instanceof String str) || str.isBlank()) throw new DbException("field '" + key + "' wajib diisi");
            return str;
        }

        private static String opt(Map<?, ?> m, String key) {
            Object v = m.get(key);
            return v instanceof String str ? str : "";
        }

        void validate(Vehicle v) {
            List<String> problems = new ArrayList<>();
            if (byId.containsKey(v.id())) problems.add("id ganda: " + v.id());
            if (v.displayName().isBlank()) problems.add("displayName kosong");
            if (!KNOWN_FAMILIES.contains(v.family())) problems.add("family tidak dikenal: " + v.family());
            String lower = (v.id() + " " + v.displayName() + " " + v.notes()).toLowerCase(Locale.ROOT);
            for (String word : FORBIDDEN_WORDS) {
                if (containsWord(lower, word)) problems.add("memuat nama kendaraan nyata: '" + word + "'");
            }
            if (!problems.isEmpty()) throw new DbException("record '" + v.id() + "' ditolak: " + problems);
        }

        /** Pencocokan kata utuh agar kata seperti "cb" tidak cocok dengan "pcb". */
        static boolean containsWord(String text, String word) {
            return java.util.regex.Pattern.compile("(?<![a-z0-9])" + java.util.regex.Pattern.quote(word)
                    + "(?![a-z0-9])").matcher(text).find();
        }

        Vehicle findById(String id) {
            return byId.get(id);
        }

        int size() {
            return byId.size();
        }
    }

    // ============================================================
    // PROGRAM UTAMA
    // ============================================================
    static int pass = 0, fail = 0;

    public static void main(String[] args) throws IOException {
        System.out.println("== Sampel 13: database kendaraan (sintetis) ==");

        String text = Files.readString(Path.of("data", "vehicles.json"), StandardCharsets.UTF_8);
        VehicleDatabase db = VehicleDatabase.load(new Json(text).parse());

        System.out.println("\n-- Isi database (" + db.size() + " record) --");
        for (Vehicle v : db.byId.values()) {
            System.out.println("  " + v.id() + " | " + v.displayName() + " | " + v.family()
                    + " | " + v.ecuIdPattern() + " | " + v.notes());
        }

        Vehicle found = db.findById("contoh-motor-b");
        System.out.println("\nlookup contoh-motor-b: " + (found == null ? "tidak ditemukan" : found.displayName()));
        System.out.println("lookup tidak-ada       : " + (db.findById("tidak-ada") == null ? "tidak ditemukan" : "??"));

        System.out.println("\n-- Pengujian --");
        check("berkas memuat tepat 3 record", db.size() == 3);
        check("lookup id dikenal berhasil", found != null && found.displayName().equals("Contoh Motor B"));
        check("lookup id asing mengembalikan null", db.findById("tidak-ada") == null);
        check("semua family dikenal", db.byId.values().stream().allMatch(v -> KNOWN_FAMILIES.contains(v.family())));

        String dupId = "{\"vehicles\":["
                + "{\"id\":\"x\",\"displayName\":\"Contoh X\",\"family\":\"contoh-baseline\"},"
                + "{\"id\":\"x\",\"displayName\":\"Contoh Y\",\"family\":\"contoh-baseline\"}]}";
        String dupMsg = expectDbFailure(dupId);
        if (dupMsg != null) System.out.println("   pesan  : " + dupMsg);
        check("id ganda ditolak", dupMsg != null && dupMsg.contains("id ganda"));

        String emptyName = "{\"vehicles\":[{\"id\":\"y\",\"displayName\":\"  \",\"family\":\"contoh-baseline\"}]}";
        String emptyMsg = expectDbFailure(emptyName);
        if (emptyMsg != null) System.out.println("   pesan  : " + emptyMsg);
        check("displayName kosong ditolak", emptyMsg != null);

        String badFamily = "{\"vehicles\":[{\"id\":\"z\",\"displayName\":\"Contoh Z\",\"family\":\"tidak-dikenal\"}]}";
        check("family tak dikenal ditolak", expectDbFailure(badFamily) != null);

        String brand = "{\"vehicles\":[{\"id\":\"w\",\"displayName\":\"Contoh Honda Vario\",\"family\":\"contoh-baseline\"}]}";
        String brandMsg = expectDbFailure(brand);
        if (brandMsg != null) System.out.println("   pesan  : " + brandMsg);
        check("nama merek nyata ditolak", brandMsg != null && brandMsg.contains("nama kendaraan nyata"));
        check("kata biasa seperti 'pcb' tidak salah tolak",
                !VehicleDatabase.containsWord("contoh pcb board", "cb"));

        String broken = "{\"vehicles\":[{\"id\":\"a\" \"displayName\":\"b\"}]}";
        String jsonMsg = null;
        try {
            new Json(broken).parse();
        } catch (JsonException e) {
            jsonMsg = e.getMessage();
        }
        if (jsonMsg != null) System.out.println("   pesan  : " + jsonMsg);
        check("JSON rusak ditolak dengan pesan jelas", jsonMsg != null);

        // Demonstrasi: record bernama nyata tidak boleh dimasukkan begitu saja.
        String realSample = "{\"vehicles\":[{\"id\":\"r\",\"displayName\":\"Honda Beat\","
                + "\"family\":\"contoh-baseline\",\"notes\":\"data nyata tanpa bukti\"}]}";
        check("record bernama nyata tanpa bukti ditolak", expectDbFailure(realSample) != null);

        System.out.println("\nRingkasan: PASS=" + pass + " FAIL=" + fail);
        if (fail > 0) {
            System.out.println("HASIL: GAGAL");
            System.exit(1);
        }
        System.out.println("HASIL: OK (data sintetis; tidak ada kendaraan nyata)");
    }

    /** Parsing + load, mengembalikan pesan kesalahan atau null bila berhasil. */
    static String expectDbFailure(String json) {
        try {
            VehicleDatabase.load(new Json(json).parse());
            return null;
        } catch (RuntimeException e) {
            return e.getMessage();
        }
    }

    static void check(String label, boolean ok) {
        if (ok) pass++; else fail++;
        System.out.println((ok ? "PASS" : "FAIL") + " - " + label);
    }
}
