package com.qmurzik.yacandy.hook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Minimal dex reader that finds methods referencing given string constants (const-string).
 * Strings stay stable across Yandex Music updates while obfuscated class names do not, so this is
 * how the module locates the few internals it needs. Pure Java, no Android or native deps.
 */
final class DexScanner {
    private DexScanner() {
    }

    /** A method found in the dex, described by type descriptors. */
    static final class MethodRef {
        final String owner;
        final String name;
        final String[] params;
        final String ret;
        final int access;

        MethodRef(String owner, String name, String[] params, String ret, int access) {
            this.owner = owner;
            this.name = name;
            this.params = params;
            this.ret = ret;
            this.access = access;
        }

        boolean isStatic() {
            return (access & 0x8) != 0;
        }

        boolean isConstructor() {
            return "<init>".equals(name);
        }

        String className() {
            return descriptorToName(owner);
        }

        /** Compact single-line form, used for the per-version cache. */
        String encode() {
            return owner + "|" + name + "|" + String.join(",", params) + "|" + ret + "|" + access;
        }

        static MethodRef decode(String s) {
            String[] p = s.split("\\|", -1);
            String[] params = p[2].isEmpty() ? new String[0] : p[2].split(",");
            return new MethodRef(p[0], p[1], params, p[3], Integer.parseInt(p[4]));
        }

        @Override
        public String toString() {
            return owner + "->" + name + "(" + String.join("", params) + ")" + ret;
        }
    }

    /** Prefix of a query that lists classes implementing an interface (descriptor follows). */
    static final String IMPL = "impl:";

    /**
     * Returns, for every requested string, the methods whose code loads it. A query starting with
     * {@link #IMPL} instead returns classes implementing that interface (as a MethodRef named "").
     */
    static Map<String, List<MethodRef>> findStringUsers(String apkPath, Collection<String> strings)
            throws IOException {
        Map<String, List<MethodRef>> out = new HashMap<>();
        for (String s : strings) out.put(s, new ArrayList<>());
        scanApk(apkPath, strings, out);
        return out;
    }

    static Map<String, List<MethodRef>> findStringUsers(Collection<String> apkPaths, Collection<String> strings)
            throws IOException {
        Map<String, List<MethodRef>> out = new HashMap<>();
        for (String s : strings) out.put(s, new ArrayList<>());
        for (String apk : apkPaths) scanApk(apk, strings, out);
        return out;
    }

    private static void scanApk(String apkPath, Collection<String> strings, Map<String, List<MethodRef>> out)
            throws IOException {
        try (ZipFile zip = new ZipFile(apkPath)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry e = entries.nextElement();
                String n = e.getName();
                if (!n.startsWith("classes") || !n.endsWith(".dex") || n.contains("/")) continue;
                byte[] dex;
                try (InputStream in = zip.getInputStream(e)) {
                    dex = readAll(in, (int) Math.max(e.getSize(), 0));
                }
                new Dex(dex).scan(strings, out);
            }
        }
    }

    static String descriptorToName(String d) {
        if (d.startsWith("L") && d.endsWith(";")) return d.substring(1, d.length() - 1).replace('/', '.');
        return d;
    }

    private static byte[] readAll(InputStream in, int hint) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream(hint > 0 ? hint : 1 << 20);
        byte[] buf = new byte[1 << 16];
        int r;
        while ((r = in.read(buf)) > 0) bos.write(buf, 0, r);
        return bos.toByteArray();
    }

    private static final class Dex {
        private final byte[] b;
        private int pos;

        Dex(byte[] b) {
            this.b = b;
        }

        private int u16(int off) {
            return (b[off] & 0xFF) | (b[off + 1] & 0xFF) << 8;
        }

        private int u32(int off) {
            return (b[off] & 0xFF) | (b[off + 1] & 0xFF) << 8 | (b[off + 2] & 0xFF) << 16 | (b[off + 3] & 0xFF) << 24;
        }

        private int uleb() {
            int result = 0;
            int shift = 0;
            int cur;
            do {
                cur = b[pos++] & 0xFF;
                result |= (cur & 0x7F) << shift;
                shift += 7;
            } while ((cur & 0x80) != 0 && shift < 35);
            return result;
        }

        private String string(int idx) {
            int off = u32(u32(0x3C) + idx * 4);
            pos = off;
            uleb(); // utf16 length
            int start = pos;
            while (b[pos] != 0) pos++;
            // MUTF-8; good enough for the identifiers and descriptors we read.
            return new String(b, start, pos - start, StandardCharsets.UTF_8);
        }

        private String type(int idx) {
            return string(u32(u32(0x44) + idx * 4));
        }

        void scan(Collection<String> wanted, Map<String, List<MethodRef>> out) {
            int stringCount = u32(0x38);
            int stringIds = u32(0x3C);
            // string index -> wanted string; matched on the raw bytes to avoid decoding 100k+ strings.
            Map<Integer, String> targets = new HashMap<>();
            List<byte[]> needles = new ArrayList<>();
            List<String> needleStrings = new ArrayList<>();
            List<String> impls = new ArrayList<>();
            for (String w : wanted) {
                if (w.startsWith(IMPL)) {
                    impls.add(w);
                    continue;
                }
                needles.add(w.getBytes(StandardCharsets.UTF_8));
                needleStrings.add(w);
            }
            if (!impls.isEmpty()) findImplementers(impls, out);
            for (int i = 0; i < stringCount; i++) {
                pos = u32(stringIds + i * 4);
                int len = uleb();
                for (int k = 0; k < needles.size(); k++) {
                    byte[] nd = needles.get(k);
                    if (nd.length != len || pos + len >= b.length || b[pos + len] != 0) continue;
                    boolean eq = true;
                    for (int j = 0; j < len; j++) {
                        if (b[pos + j] != nd[j]) {
                            eq = false;
                            break;
                        }
                    }
                    if (eq) targets.put(i, needleStrings.get(k));
                }
            }
            if (targets.isEmpty()) return;

            int classCount = u32(0x60);
            int classDefs = u32(0x64);
            for (int c = 0; c < classCount; c++) {
                int def = classDefs + c * 32;
                int dataOff = u32(def + 24);
                if (dataOff == 0) continue;
                pos = dataOff;
                int sf = uleb(), inf = uleb(), dm = uleb(), vm = uleb();
                for (int i = 0; i < sf + inf; i++) {
                    uleb();
                    uleb();
                }
                for (int group = 0; group < 2; group++) {
                    int count = group == 0 ? dm : vm;
                    int methodIdx = 0;
                    for (int i = 0; i < count; i++) {
                        methodIdx += uleb();
                        int access = uleb();
                        int codeOff = uleb();
                        if (codeOff == 0) continue;
                        int saved = pos;
                        String hit = scanCode(codeOff, targets);
                        if (hit != null) out.get(hit).add(method(methodIdx, access));
                        pos = saved;
                    }
                }
            }
        }

        private void findImplementers(List<String> queries, Map<String, List<MethodRef>> out) {
            // Resolve each wanted interface to its type index once, then compare indices.
            Map<Integer, String> wanted = new HashMap<>();
            int typeCount = u32(0x40);
            for (String q : queries) {
                String d = q.substring(IMPL.length());
                for (int t = 0; t < typeCount; t++) {
                    if (type(t).equals(d)) {
                        wanted.put(t, q);
                        break;
                    }
                }
            }
            if (wanted.isEmpty()) return;
            int classCount = u32(0x60);
            int classDefs = u32(0x64);
            for (int c = 0; c < classCount; c++) {
                int def = classDefs + c * 32;
                int ifacesOff = u32(def + 12);
                if (ifacesOff == 0) continue;
                int n = u32(ifacesOff);
                for (int k = 0; k < n; k++) {
                    String q = wanted.get(u16(ifacesOff + 4 + k * 2));
                    if (q != null) out.get(q).add(new MethodRef(type(u32(def)), "", new String[0], "V", u32(def + 4)));
                }
            }
        }

        /** Walks the bytecode of one method; returns the first wanted string it loads. */
        private String scanCode(int codeOff, Map<Integer, String> targets) {
            int insnsSize = u32(codeOff + 12);
            int start = codeOff + 16;
            int i = 0;
            while (i < insnsSize) {
                int at = start + i * 2;
                int unit = u16(at);
                int op = unit & 0xFF;
                if (op == 0x1A) {
                    String s = targets.get(u16(at + 2));
                    if (s != null) return s;
                } else if (op == 0x1B) {
                    String s = targets.get(u32(at + 2));
                    if (s != null) return s;
                } else if (op == 0x00 && unit != 0) {
                    // Switch / array payload pseudo-instructions.
                    if (unit == 0x0100) {
                        i += u16(at + 2) * 2 + 4;
                        continue;
                    } else if (unit == 0x0200) {
                        i += u16(at + 2) * 4 + 2;
                        continue;
                    } else if (unit == 0x0300) {
                        int width = u16(at + 2);
                        long size = u32(at + 4) & 0xFFFFFFFFL;
                        i += (int) ((size * width + 1) / 2) + 4;
                        continue;
                    }
                }
                i += WIDTH[op];
            }
            return null;
        }

        private MethodRef method(int idx, int access) {
            int m = u32(0x5C) + idx * 8;
            int classIdx = u16(m);
            int protoIdx = u16(m + 2);
            int nameIdx = u32(m + 4);
            int p = u32(0x4C) + protoIdx * 12;
            int retIdx = u32(p + 4);
            int paramsOff = u32(p + 8);
            String[] params;
            if (paramsOff == 0) {
                params = new String[0];
            } else {
                int n = u32(paramsOff);
                params = new String[n];
                for (int k = 0; k < n; k++) params[k] = type(u16(paramsOff + 4 + k * 2));
            }
            return new MethodRef(type(classIdx), string(nameIdx), params, type(retIdx), access);
        }
    }

    /** Instruction widths in 16-bit code units, indexed by opcode. */
    private static final int[] WIDTH = new int[256];

    static {
        java.util.Arrays.fill(WIDTH, 1);
        int[][] ranges = {
                // {from, to, width}
                {0x02, 0x02, 2}, {0x03, 0x03, 3}, {0x05, 0x05, 2}, {0x06, 0x06, 3}, {0x08, 0x08, 2},
                {0x09, 0x09, 3}, {0x13, 0x13, 2}, {0x14, 0x14, 3}, {0x15, 0x16, 2}, {0x17, 0x17, 3},
                {0x18, 0x18, 5}, {0x19, 0x1A, 2}, {0x1B, 0x1B, 3}, {0x1C, 0x1C, 2}, {0x1F, 0x20, 2},
                {0x22, 0x23, 2}, {0x24, 0x26, 3}, {0x29, 0x29, 2}, {0x2A, 0x2C, 3}, {0x2D, 0x3D, 2},
                {0x44, 0x6D, 2}, {0x6E, 0x72, 3}, {0x74, 0x78, 3}, {0x90, 0xAF, 2}, {0xD0, 0xE2, 2},
                {0xFA, 0xFB, 4}, {0xFC, 0xFD, 3}, {0xFE, 0xFF, 2},
        };
        for (int[] r : ranges) {
            for (int op = r[0]; op <= r[1]; op++) WIDTH[op] = r[2];
        }
    }
}
