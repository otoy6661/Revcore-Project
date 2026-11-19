import java.util.ArrayList;
import java.util.List;

/**
 * Sampel 10: ECU simulasi di memori (BUKAN perangkat keras).
 *
 * Simulator menerima frame dari "tester", memeriksa checksum, lalu memilih
 * respons berdasarkan service + parameter pertama:
 *   0x71 / 0x00 -> identifikasi   (respons sintetis)
 *   0x74 / 0x01 -> baca DTC       (respons sintetis tetap)
 *   0x60 / 0x01 -> hapus DTC      (ACK sintetis)
 *   lainnya     -> negatif 0x7F
 * Simulator hanya membuktikan logika perangkat lunak, bukan kompatibilitas
 * dengan kendaraan nyata.
 *
 * Jalankan dari direktori sampel ini:
 *   javac -d out src/Main.java
 *   java -cp out Main
 */
public class Main {

    // ============================================================
    // UTILITAS FRAME
    // ============================================================
    static int checksum(byte[] body) {
        int sum = 0;
        for (byte b : body) sum += b & 0xFF;
        return (256 - (sum & 0xFF)) & 0xFF;
    }

    /** Menyusun frame dari body (header..params) dan menambahkan checksum. */
    static byte[] buildFrame(int... body) {
        byte[] out = new byte[body.length + 1];
        for (int i = 0; i < body.length; i++) out[i] = (byte) body[i];
        out[body.length] = (byte) checksum(out);
        return out;
    }

    static byte[] hexToBytes(String hex) {
        String[] parts = hex.trim().split("\\s+");
        byte[] out = new byte[parts.length];
        for (int i = 0; i < parts.length; i++) out[i] = (byte) Integer.parseInt(parts[i], 16);
        return out;
    }

    static String hex(byte[] data) {
        StringBuilder sb = new StringBuilder();
        for (byte b : data) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(String.format("%02X", b & 0xFF));
        }
        return sb.toString();
    }

    static int checksumOfFrame(byte[] frame) {
        byte[] body = java.util.Arrays.copyOf(frame, frame.length - 1);
        return checksum(body);
    }

    static boolean isValidFrame(byte[] frame) {
        return frame.length >= 4 && checksumOfFrame(frame) == (frame[frame.length - 1] & 0xFF);
    }

    // ============================================================
    // ECU SIMULASI
    // ============================================================
    static final class SimulatedEcu {
        final List<String> log = new ArrayList<>();

        /** Menerima satu frame permintaan; mengembalikan satu frame respons. */
        byte[] receive(byte[] request) {
            log.add("TX " + hex(request));

            // Langkah 1: checksum wajib valid. Frame rusak tidak ditanggapi.
            if (!isValidFrame(request)) {
                log.add("RX (tidak ada) - checksum tidak valid");
                return null;
            }

            int service = request[2] & 0xFF;
            int param = request.length > 3 ? request[3] & 0xFF : 0;
            byte[] response = route(service, param);
            log.add("RX " + hex(response));
            return response;
        }

        private byte[] route(int service, int param) {
            if (service == 0x71 && param == 0x00) {
                // Identifikasi sintetis: 3 byte data contoh, bukan ID kendaraan nyata.
                return buildFrame(0x72, 0x06, 0x71, 0x00, 0x01, 0x02, 0x03);
            }
            if (service == 0x74 && param == 0x01) {
                // Data tetap: payload 02 01 01 07 01 -> P0107, P0117.
                return hexToBytes("72 0A 74 01 02 01 01 07 01 03");
            }
            if (service == 0x60 && param == 0x01) {
                // ACK hapus DTC: service 0x60, param 0x01, data 0x00.
                return buildFrame(0x72, 0x05, 0x60, 0x01, 0x00);
            }
            // Negatif: service 0x7F, service ditolak, kode 0x12 (subFunction not supported).
            return buildFrame(0x72, 0x05, 0x7F, service, 0x12);
        }
    }

    // ============================================================
    // PROGRAM UTAMA
    // ============================================================
    static int pass = 0, fail = 0;

    public static void main(String[] args) {
        System.out.println("== Sampel 10: ECU simulasi ==");
        SimulatedEcu ecu = new SimulatedEcu();

        System.out.println("\n-- Sesi contoh --");
        byte[] id = send(ecu, hexToBytes("72 05 71 00 18"));
        byte[] dtc = send(ecu, hexToBytes("72 05 74 01 14"));
        byte[] clear = send(ecu, hexToBytes("72 05 60 01 28"));
        byte[] live = send(ecu, hexToBytes("72 05 71 7A 9E"));
        byte[] bad = send(ecu, hexToBytes("72 05 74 01 15"));

        System.out.println("\n-- Log TX/RX --");
        for (String line : ecu.log) System.out.println("  " + line);

        System.out.println("\n-- Pengujian --");
        check("identifikasi: respons service 0x71", id != null && (id[2] & 0xFF) == 0x71);
        check("identifikasi: checksum respons valid", id != null && isValidFrame(id));
        check("baca DTC: service 0x74", dtc != null && (dtc[2] & 0xFF) == 0x74);
        check("baca DTC: payload 02 01 01 07 01",
                dtc != null && hex(java.util.Arrays.copyOfRange(dtc, 4, dtc.length - 1)).equals("02 01 01 07 01"));
        check("baca DTC: frame sama persis dengan 72 0A 74 01 02 01 01 07 01 03",
                dtc != null && hex(dtc).equals("72 0A 74 01 02 01 01 07 01 03"));
        check("hapus DTC: ACK service 0x60 dan checksum valid",
                clear != null && (clear[2] & 0xFF) == 0x60 && isValidFrame(clear));
        check("checksum rusak (72 05 74 01 15) ditolak tanpa respons", bad == null);
        check("service tak dikenal (0x71/0x7A) -> negatif 0x7F",
                live != null && (live[2] & 0xFF) == 0x7F && (live[3] & 0xFF) == 0x71);
        byte[] unknown = send(ecu, hexToBytes("72 05 55 01 33")); // 72+05+55+01 = 0xCD -> checksum 0x33
        check("service 0x55 -> negatif 0x7F menolak 0x55",
                unknown != null && (unknown[2] & 0xFF) == 0x7F
                        && (unknown[3] & 0xFF) == 0x55 && (unknown[4] & 0xFF) == 0x12);
        check("respons sintetis identifikasi punya checksum benar",
                id != null && (id[id.length - 1] & 0xFF) == checksumOfFrame(id));
        check("respons sintetis ACK punya checksum benar",
                clear != null && (clear[clear.length - 1] & 0xFF) == checksumOfFrame(clear));

        System.out.println("\nRingkasan: PASS=" + pass + " FAIL=" + fail);
        if (fail > 0) {
            System.out.println("HASIL: GAGAL");
            System.exit(1);
        }
        System.out.println("HASIL: OK (simulasi perangkat lunak, bukan uji kendaraan)");
    }

    static byte[] send(SimulatedEcu ecu, byte[] request) {
        byte[] response = ecu.receive(request);
        System.out.println("permintaan " + hex(request) + " -> "
                + (response == null ? "tidak ada respons" : hex(response)));
        return response;
    }

    static void check(String label, boolean ok) {
        if (ok) pass++; else fail++;
        System.out.println((ok ? "PASS" : "FAIL") + " - " + label);
    }
}
