import java.util.ArrayList;
import java.util.List;

/**
 * Sampel 11: abstraksi transport.
 *
 * Lapisan diagnostik HANYA berbicara dengan antarmuka `Transport`:
 * open(), close(), write(byte[]), read(timeoutMs). Ia tidak tahu apakah
 * di balik layar ada USB, Bluetooth, atau simulator.
 *
 * Dua implementasi:
 *   - SimulatedTransport : membungkus logika ECU simulasi (seperti sampel 10).
 *   - RecordingTransport : dekorator yang mencatat setiap byte yang lewat.
 *
 * Pemetaan ke USB serial nyata ada di README dan berlabel REFERENCE;
 * sampel ini tidak menguji perangkat keras apa pun.
 *
 * Jalankan dari direktori sampel ini:
 *   javac -d out src/Main.java
 *   java -cp out Main
 */
public class Main {

    // ============================================================
    // UTILITAS
    // ============================================================
    static int checksum(byte[] body) {
        int s = 0;
        for (byte b : body) s += b & 0xFF;
        return (256 - (s & 0xFF)) & 0xFF;
    }

    static boolean isValidFrame(byte[] f) {
        if (f.length < 4) return false;
        byte[] body = java.util.Arrays.copyOf(f, f.length - 1);
        return checksum(body) == (f[f.length - 1] & 0xFF);
    }

    static byte[] buildFrame(int... body) {
        byte[] out = new byte[body.length + 1];
        for (int i = 0; i < body.length; i++) out[i] = (byte) body[i];
        out[body.length] = (byte) checksum(out);
        return out;
    }

    static byte[] hexToBytes(String hex) {
        String[] p = hex.trim().split("\\s+");
        byte[] out = new byte[p.length];
        for (int i = 0; i < p.length; i++) out[i] = (byte) Integer.parseInt(p[i], 16);
        return out;
    }

    static String hex(byte[] d) {
        StringBuilder sb = new StringBuilder();
        for (byte b : d) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(String.format("%02X", b & 0xFF));
        }
        return sb.toString();
    }

    // ============================================================
    // ANTARMUKA TRANSPORT
    // ============================================================
    /**
     * Satu-satunya kontrak yang dilihat lapisan diagnostik.
     * Implementasi boleh berupa simulator, USB, atau apa pun.
     */
    interface Transport {
        void open() throws TransportException;

        void close();

        /** Mengirim satu frame permintaan utuh. */
        void write(byte[] request) throws TransportException;

        /** Menunggu respons sampai timeoutMs. Mengembalikan null bila tidak ada. */
        byte[] read(int timeoutMs) throws TransportException;

        String describe();

        class TransportException extends Exception {
            private static final long serialVersionUID = 1L;

            public TransportException(String message) { super(message); }
        }
    }

    // ============================================================
    // IMPLEMENTASI 1: SIMULATOR
    // ============================================================
    /** Membungkus logika ECU simulasi. Tidak ada perangkat keras. */
    static final class SimulatedTransport implements Transport {
        private final byte[][] scriptedResponses;
        private boolean open;
        private byte[] pending;

        /** Respons diambil berurutan sesuai urutan write. null = tanpa respons. */
        SimulatedTransport(byte[]... scriptedResponses) {
            this.scriptedResponses = scriptedResponses;
        }

        private int step = 0;

        @Override
        public void open() throws TransportException {
            if (open) throw new TransportException("transport sudah terbuka");
            open = true;
        }

        @Override
        public void close() {
            open = false;
        }

        @Override
        public void write(byte[] request) throws TransportException {
            if (!open) throw new TransportException("write sebelum open");
            if (!isValidFrame(request)) {
                pending = null; // frame rusak: ECU simulasi diam
                step++;
                return;
            }
            int service = request[2] & 0xFF;
            int param = request[3] & 0xFF;
            if (scriptedResponses.length > 0) {
                pending = step < scriptedResponses.length ? scriptedResponses[step] : null;
                step++;
            } else {
                pending = route(service, param);
            }
        }

        private byte[] route(int service, int param) {
            if (service == 0x71 && param == 0x00) return hexToBytes("72 06 71 00 01 02 03 11");
            if (service == 0x74 && param == 0x01) return hexToBytes("72 0A 74 01 02 01 01 07 01 03");
            if (service == 0x60 && param == 0x01) return hexToBytes("72 05 60 01 00 28");
            return buildFrame(0x72, 0x05, 0x7F, service, 0x12);
        }

        @Override
        public byte[] read(int timeoutMs) throws TransportException {
            if (!open) throw new TransportException("read sebelum open");
            byte[] r = pending;
            pending = null;
            return r; // simulator menjawab seketika; timeout tidak disimulasikan
        }

        @Override
        public String describe() {
            return "SimulatedTransport (tidak ada perangkat keras)";
        }
    }

    // ============================================================
    // IMPLEMENTASI 2: DEKORATOR PEREKAM
    // ============================================================
    /** Dekorator: meneruskan semua panggilan sambil mencatat byte yang lewat. */
    static final class RecordingTransport implements Transport {
        private final Transport inner;
        final List<String> log = new ArrayList<>();

        RecordingTransport(Transport inner) {
            this.inner = inner;
        }

        @Override
        public void open() throws TransportException {
            log.add("open()");
            inner.open();
        }

        @Override
        public void close() {
            log.add("close()");
            inner.close();
        }

        @Override
        public void write(byte[] request) throws TransportException {
            log.add("TX " + request.length + " byte: " + hex(request));
            inner.write(request);
        }

        @Override
        public byte[] read(int timeoutMs) throws TransportException {
            byte[] r = inner.read(timeoutMs);
            log.add("RX " + (r == null ? "timeout " + timeoutMs + " ms" : r.length + " byte: " + hex(r)));
            return r;
        }

        @Override
        public String describe() {
            return "RecordingTransport -> " + inner.describe();
        }
    }

    // ============================================================
    // LAPISAN DIAGNOSTIK (hanya mengenal Transport)
    // ============================================================
    /** Contoh lapisan yang tidak tahu apa pun soal USB. */
    static final class DiagnosticLayer {
        private final Transport transport;

        DiagnosticLayer(Transport transport) {
            this.transport = transport;
        }

        String identify() throws Transport.TransportException {
            transport.write(hexToBytes("72 05 71 00 18"));
            byte[] r = transport.read(500);
            if (r == null) return "NO_RESPONSE";
            return "service=0x" + String.format("%02X", r[2] & 0xFF)
                    + " payload=" + hex(java.util.Arrays.copyOfRange(r, 4, r.length - 1));
        }
    }

    // ============================================================
    // PROGRAM UTAMA
    // ============================================================
    static int pass = 0, fail = 0;

    public static void main(String[] args) throws Exception {
        System.out.println("== Sampel 11: abstraksi transport ==");

        SimulatedTransport sim = new SimulatedTransport();
        RecordingTransport rec = new RecordingTransport(sim);
        DiagnosticLayer layer = new DiagnosticLayer(rec);

        System.out.println("\nTransport: " + rec.describe());
        rec.open();
        String id = layer.identify();
        rec.close();
        System.out.println("hasil identify: " + id);
        System.out.println("\n-- Log perekaman --");
        for (String line : rec.log) System.out.println("  " + line);

        // Skenario tanpa respons: implementasi berbeda, lapisan yang sama.
        RecordingTransport silent = new RecordingTransport(new SimulatedTransport((byte[]) null));
        silent.open();
        String noResp = new DiagnosticLayer(silent).identify();
        silent.close();
        System.out.println("\nskenario tanpa respons -> " + noResp);

        System.out.println("\n-- Pengujian --");
        Transport general = rec; // hanya melihat antarmuka
        check("lapisan diagnostik memakai tipe Transport, bukan kelas simulator",
                general.describe().contains("RecordingTransport"));
        check("identifikasi lewat simulator: service 0x71", id.startsWith("service=0x71"));
        check("payload identifikasi 01 02 03", id.contains("payload=01 02 03"));
        check("perekam mencatat open, TX, RX, close berurutan",
                rec.log.size() == 4 && rec.log.get(0).equals("open()")
                        && rec.log.get(1).startsWith("TX 5 byte") && rec.log.get(2).startsWith("RX 8 byte")
                        && rec.log.get(3).equals("close()"));
        check("byte TX identik dengan frame asli", rec.log.get(1).endsWith("72 05 71 00 18"));
        check("tanpa respons -> NO_RESPONSE, bukan daftar kosong", noResp.equals("NO_RESPONSE"));
        check("write sebelum open ditolak",
                rejected(() -> new SimulatedTransport().write(hexToBytes("72 05 71 00 18"))));
        check("open dua kali ditolak", rejected(() -> {
            SimulatedTransport s = new SimulatedTransport();
            s.open();
            s.open();
        }));
        check("frame checksum rusak tidak dibalas", noReplyOnBadChecksum());
        Transport viaInterface = new SimulatedTransport(hexToBytes("72 05 7F 74 12 84"));
        viaInterface.open();
        viaInterface.write(hexToBytes("72 05 74 01 14"));
        byte[] neg = viaInterface.read(100);
        viaInterface.close();
        check("respons negatif 0x7F tetap diteruskan sebagai byte",
                neg != null && (neg[2] & 0xFF) == 0x7F && hex(neg).equals("72 05 7F 74 12 84"));

        System.out.println("\nRingkasan: PASS=" + pass + " FAIL=" + fail);
        if (fail > 0) {
            System.out.println("HASIL: GAGAL");
            System.exit(1);
        }
        System.out.println("HASIL: OK (transport disimulasikan, tidak ada perangkat keras)");
    }

    interface Action {
        void run() throws Exception;
    }

    /** True bila aksi melempar exception (dipakai untuk menguji penolakan kontrak). */
    static boolean rejected(Action action) {
        try {
            action.run();
            return false;
        } catch (Exception e) {
            return true;
        }
    }

    static boolean noReplyOnBadChecksum() {
        try {
            SimulatedTransport s = new SimulatedTransport();
            s.open();
            s.write(hexToBytes("72 05 74 01 15")); // checksum salah
            return s.read(100) == null;
        } catch (Exception e) {
            return false;
        }
    }

    static void check(String label, boolean ok) {
        if (ok) pass++; else fail++;
        System.out.println((ok ? "PASS" : "FAIL") + " - " + label);
    }
}
