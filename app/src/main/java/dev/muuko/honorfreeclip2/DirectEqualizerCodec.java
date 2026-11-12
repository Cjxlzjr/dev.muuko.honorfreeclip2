package dev.muuko.honorfreeclip2;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Independent FreeClip 2 custom EQ command encoding.
 *
 * The user's SmartAudio APK call graph shows:
 * EqAdjustService.setEqAdjust(String,int,String,byte[],int)
 *  -> sg.d.setEqAdjust -> q9.b.a1(String,int,int,byte[],byte[],byte)
 *  -> mbb.d.b4 -> q9.b.I0.
 * The MBB formatter constructs TLV 2B/49 with preset ID, band count,
 * operation=1, ten signed 0.1dB gains, UTF-8 name and CRC16/XMODEM.
 *
 * Independent EQ writing has been reported successful by the user on FreeClip 2
 * with V0.8 for a limited single-band edit. Full ten-band writes and renaming
 * in V0.9 still require separate on-device verification.
 */
final class DirectEqualizerCodec {
    static final int WRITE_OPERATION = 1;
    private DirectEqualizerCodec() { }

    static byte[] encode(int id, String name, int[] gains) {
        return encodeOperation(id,name,gains,WRITE_OPERATION);
    }

    /** Delete operation=2 is documented in OpenFreebuds but not yet FreeClip 2 verified. */
    static byte[] encodeDelete(int id,String name,int[] gains) {
        return encodeOperation(id,name,gains,2);
    }

    private static byte[] encodeOperation(int id,String name,int[] gains,int operation) {
        if (operation != 1 && operation != 2) return null;
        if (id < 0x64 || id > 0x66 || !EqSavePolicy.validName(name)
                || !EqSavePolicy.validGains(gains)) return null;
        byte[] utf8 = name.trim().getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        // Service=2B, command=49; TLVs 01=id, 02=band count,
        // 05=operation, 03=gains, 04=UTF8 name (vendor-defined order).
        int[] head = {0x2b, 0x49, 0x01, 0x01, id, 0x02, 0x01, 10,
                0x05, 0x01, operation, 0x03, 10};
        for (int b : head) body.write(b);
        for (int gain : gains) body.write(gain & 0xff);
        body.write(0x04);
        body.write(utf8.length);
        body.write(utf8, 0, utf8.length);
        byte[] data = body.toByteArray();
        int len = data.length + 1; // AAM frame header length is body+1 (CRC contributes two bytes to total).
        // Frame length field is defined by the existing Huawei 5A framing convention.
        ByteArrayOutputStream frame = new ByteArrayOutputStream();
        frame.write(0x5A); frame.write(0x00);
        frame.write(len & 255); frame.write((len >> 8) & 255);
        frame.write(data, 0, data.length);
        byte[] raw = frame.toByteArray();
        int crc = FreeClipProtocol.crc16(raw, raw.length);
        frame.write((crc >> 8) & 255); frame.write(crc & 255);
        return frame.toByteArray();
    }

    static boolean isExactReport(EqualizerState state, int id, String name, int[] gains) {
        if (state == null || !state.supported || state.selectedId != id
                || state.bandCount != 10 || !EqSavePolicy.validGains(gains)
                || name == null || !name.trim().equals(state.selectedName)
                || state.selectedGains == null || state.selectedGains.size() != 10) return false;
        for (int i=0; i<10; i++) if (state.selectedGains.get(i) != gains[i]) return false;
        return true;
    }

    static boolean sameSnapshot(EqualizerState fresh, EqualizerState initial) {
        if (initial == null || fresh == null || !fresh.supported
                || initial.selectedId != fresh.selectedId
                || initial.bandCount != fresh.bandCount
                || !String.valueOf(initial.selectedName).equals(String.valueOf(fresh.selectedName))
                || initial.selectedGains == null || fresh.selectedGains == null
                || !initial.selectedGains.equals(fresh.selectedGains)) return false;
        // On V1.1 firmware reports with TLV08, protect all three slots during old
        // single-slot edits and one-step undo, not just the selected slot.
        if (initial.customCatalogKnown && fresh.customCatalogKnown
                && !CustomPresetPolicy.sameCatalog(initial,fresh)) return false;
        return true;
    }

    static String describe(byte[] payload) {
        if (payload == null) return "invalid";
        StringBuilder out = new StringBuilder(payload.length*2);
        for (byte b:payload) out.append(Character.forDigit((b >>> 4)&15,16))
                .append(Character.forDigit(b&15,16));
        return out.toString().toUpperCase(java.util.Locale.ROOT);
    }
}
