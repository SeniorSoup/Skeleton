import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.plaf.basic.*;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.Consumer;
import java.util.regex.*;
import java.util.stream.*;
import java.util.zip.*;

public class Skeleton {
    static final String VERSION = "1.0", UA = "SkeletonLauncher/" + VERSION;
    static final Path ROOT = Paths.get(System.getProperty("skeleton.home",
            (System.getenv("APPDATA") != null ? System.getenv("APPDATA") : System.getProperty("user.home")) + File.separator + ".skeleton"));
    static final String API = "https://api.modrinth.com/v2";
    static final String[] PERF = {"sodium", "lithium", "ferrite-core", "modernfix", "entityculling", "immediatelyfast"};
    static final String AIKAR = "-XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:MaxGCPauseMillis=200 -XX:+UnlockExperimentalVMOptions "
            + "-XX:+DisableExplicitGC -XX:G1NewSizePercent=30 -XX:G1MaxNewSizePercent=40 -XX:G1HeapRegionSize=8M "
            + "-XX:G1ReservePercent=20 -XX:G1HeapWastePercent=5 -XX:G1MixedGCCountTarget=4 -XX:InitiatingHeapOccupancyPercent=15 "
            + "-XX:G1MixedGCLiveThresholdPercent=90 -XX:G1RSetUpdatingPauseTimePercent=5 -XX:SurvivorRatio=32 "
            + "-XX:+PerfDisableSharedMem -XX:MaxTenuringThreshold=1";

    static final class Json {
        final String s; int i;
        Json(String s) { this.s = s; }
        static Object parse(String s) { Json j = new Json(s); return j.val(); }
        void ws() { while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++; }
        Object val() {
            ws();
            char c = s.charAt(i);
            switch (c) {
                case '{': return obj();
                case '[': return arr();
                case '"': return str();
                case 't': i += 4; return Boolean.TRUE;
                case 'f': i += 5; return Boolean.FALSE;
                case 'n': i += 4; return null;
                default: return num();
            }
        }
        Map<String, Object> obj() {
            Map<String, Object> m = new LinkedHashMap<>();
            i++; ws();
            if (s.charAt(i) == '}') { i++; return m; }
            for (;;) {
                ws(); String k = str(); ws(); i++;
                m.put(k, val()); ws();
                if (s.charAt(i++) == '}') break;
            }
            return m;
        }
        List<Object> arr() {
            List<Object> l = new ArrayList<>();
            i++; ws();
            if (s.charAt(i) == ']') { i++; return l; }
            for (;;) {
                l.add(val()); ws();
                if (s.charAt(i++) == ']') break;
            }
            return l;
        }
        String str() {
            i++;
            StringBuilder b = new StringBuilder();
            for (;;) {
                char c = s.charAt(i++);
                if (c == '"') break;
                if (c == '\\') {
                    char e = s.charAt(i++);
                    switch (e) {
                        case 'n': b.append('\n'); break;
                        case 't': b.append('\t'); break;
                        case 'r': b.append('\r'); break;
                        case 'b': b.append('\b'); break;
                        case 'f': b.append('\f'); break;
                        case 'u': b.append((char) Integer.parseInt(s.substring(i, i + 4), 16)); i += 4; break;
                        default: b.append(e);
                    }
                } else b.append(c);
            }
            return b.toString();
        }
        Object num() {
            int st = i;
            while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) i++;
            String t = s.substring(st, i);
            if (t.matches("-?\\d+")) return Long.parseLong(t);
            return Double.parseDouble(t);
        }
        static String write(Object o) {
            StringBuilder b = new StringBuilder();
            w(b, o);
            return b.toString();
        }
        static void w(StringBuilder b, Object o) {
            if (o == null) b.append("null");
            else if (o instanceof Map) {
                b.append('{');
                boolean f = true;
                for (Map.Entry<?, ?> e : ((Map<?, ?>) o).entrySet()) {
                    if (!f) b.append(',');
                    f = false;
                    w(b, String.valueOf(e.getKey())); b.append(':'); w(b, e.getValue());
                }
                b.append('}');
            } else if (o instanceof List) {
                b.append('[');
                boolean f = true;
                for (Object e : (List<?>) o) { if (!f) b.append(','); f = false; w(b, e); }
                b.append(']');
            } else if (o instanceof Number || o instanceof Boolean) b.append(o);
            else {
                b.append('"');
                for (char c : String.valueOf(o).toCharArray()) {
                    if (c == '"' || c == '\\') b.append('\\').append(c);
                    else if (c == '\n') b.append("\\n");
                    else if (c == '\r') b.append("\\r");
                    else if (c == '\t') b.append("\\t");
                    else if (c < 0x20) b.append(String.format("\\u%04x", (int) c));
                    else b.append(c);
                }
                b.append('"');
            }
        }
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> M(Object o) { return o instanceof Map ? (Map<String, Object>) o : new LinkedHashMap<>(); }
    @SuppressWarnings("unchecked")
    static List<Object> L(Object o) { return o instanceof List ? (List<Object>) o : new ArrayList<>(); }
    static String S(Object o) { return o == null ? null : String.valueOf(o); }
    static long N(Object o, long d) { return o instanceof Number ? ((Number) o).longValue() : d; }
    static Object at(Object o, Object... path) {
        for (Object k : path) {
            if (o == null) return null;
            if (k instanceof String && o instanceof Map) o = ((Map<?, ?>) o).get(k);
            else if (k instanceof Integer && o instanceof List) {
                List<?> l = (List<?>) o; int ix = (Integer) k;
                o = ix < l.size() ? l.get(ix) : null;
            } else return null;
        }
        return o;
    }
    static String enc(String s) { return URLEncoder.encode(s, StandardCharsets.UTF_8); }
    static String cap(String s) { return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1); }
    static String jsonArr(List<String> l) { return Json.write(new ArrayList<Object>(l)); }

    static final HttpClient HTTP = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(15)).build();

    static String get(String url) throws IOException {
        try {
            HttpRequest r = HttpRequest.newBuilder(URI.create(url)).header("User-Agent", UA).timeout(Duration.ofSeconds(30)).build();
            HttpResponse<String> res = HTTP.send(r, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() / 100 != 2) throw new IOException("HTTP " + res.statusCode() + " from " + url);
            return res.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted");
        }
    }

    static String hash(Path p, String algo) throws IOException {
        try (InputStream in = Files.newInputStream(p)) {
            MessageDigest md = MessageDigest.getInstance(algo);
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
            StringBuilder b = new StringBuilder();
            for (byte x : md.digest()) b.append(String.format("%02x", x));
            return b.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
    }

    static void download(String url, Path dest, String hash, String algo, long size) throws IOException {
        if (Files.exists(dest)) {
            if (size >= 0 && Files.size(dest) == size) return;
            if (size < 0 && hash != null && hash(dest, algo).equalsIgnoreCase(hash)) return;
        }
        Files.createDirectories(dest.getParent());
        IOException last = null;
        for (int a = 0; a < 3; a++) {
            Path tmp = dest.resolveSibling(dest.getFileName() + ".part");
            try {
                HttpRequest r = HttpRequest.newBuilder(URI.create(url)).header("User-Agent", UA).timeout(Duration.ofMinutes(5)).build();
                HttpResponse<InputStream> res = HTTP.send(r, HttpResponse.BodyHandlers.ofInputStream());
                if (res.statusCode() / 100 != 2) { res.body().close(); throw new IOException("HTTP " + res.statusCode() + " from " + url); }
                try (InputStream in = res.body()) { Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING); }
                if (hash != null && !hash(tmp, algo).equalsIgnoreCase(hash)) throw new IOException("Checksum mismatch for " + dest.getFileName());
                Files.move(tmp, dest, StandardCopyOption.REPLACE_EXISTING);
                return;
            } catch (IOException e) {
                last = e;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted");
            }
        }
        throw last;
    }

    static Path safe(Path base, String rel) throws IOException {
        Path b = base.toAbsolutePath().normalize();
        Path p = b.resolve(rel).normalize();
        if (!p.startsWith(b)) throw new IOException("Unsafe path in archive: " + rel);
        return p;
    }

    static void unzip(InputStream src, Path dir, List<String> exclude) throws IOException {
        try (ZipInputStream z = new ZipInputStream(src)) {
            ZipEntry e;
            while ((e = z.getNextEntry()) != null) {
                if (e.isDirectory()) continue;
                boolean skip = false;
                for (String x : exclude) if (e.getName().startsWith(x)) skip = true;
                if (skip) continue;
                Path out = safe(dir, e.getName());
                Files.createDirectories(out.getParent());
                Files.copy(z, out, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    static void deleteTree(Path p) throws IOException {
        if (!Files.exists(p)) return;
        try (Stream<Path> s = Files.walk(p)) {
            for (Path x : s.sorted(Comparator.reverseOrder()).collect(Collectors.toList())) Files.delete(x);
        }
    }

    interface Prog { void set(String label, int done, int total); }

    static void runAll(List<Callable<Void>> tasks, Prog p, String label) throws Exception {
        if (tasks.isEmpty()) return;
        ExecutorService ex = Executors.newFixedThreadPool(16);
        AtomicInteger done = new AtomicInteger();
        List<Future<Void>> fs = new ArrayList<>();
        for (Callable<Void> t : tasks) fs.add(ex.submit(() -> { t.call(); p.set(label, done.incrementAndGet(), tasks.size()); return null; }));
        try {
            for (Future<Void> f : fs) f.get();
        } catch (ExecutionException e) {
            ex.shutdownNow();
            throw e.getCause() instanceof Exception ? (Exception) e.getCause() : e;
        } finally {
            ex.shutdown();
        }
    }

    static final class Inst {
        String id, name, mc, loader = "vanilla", loaderVer = "", jvm = "", java = "";
        int ram = 4096;
        boolean opt = true;
        Path dir() { return ROOT.resolve("instances").resolve(id); }
        Path game() { return dir().resolve("minecraft"); }
        String sub() { return mc + ", " + (loader.equals("vanilla") ? "Vanilla" : cap(loader) + " " + loaderVer); }
        void save() throws IOException {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", name); m.put("mc", mc); m.put("loader", loader); m.put("loaderVer", loaderVer);
            m.put("ram", (long) ram); m.put("jvm", jvm); m.put("java", java); m.put("opt", opt);
            Files.createDirectories(game());
            Files.writeString(dir().resolve("instance.json"), Json.write(m));
        }
        static Inst load(Path d) {
            try {
                Map<String, Object> m = M(Json.parse(Files.readString(d.resolve("instance.json"))));
                Inst i = new Inst();
                i.id = d.getFileName().toString();
                i.name = S(m.get("name")); i.mc = S(m.get("mc"));
                i.loader = m.get("loader") == null ? "vanilla" : S(m.get("loader"));
                i.loaderVer = m.get("loaderVer") == null ? "" : S(m.get("loaderVer"));
                i.ram = (int) N(m.get("ram"), 4096);
                i.jvm = m.get("jvm") == null ? "" : S(m.get("jvm"));
                i.java = m.get("java") == null ? "" : S(m.get("java"));
                i.opt = !Boolean.FALSE.equals(m.get("opt"));
                return i.name == null || i.mc == null ? null : i;
            } catch (Exception e) { return null; }
        }
        public String toString() { return name; }
    }

    static List<Inst> loadInstances() {
        List<Inst> l = new ArrayList<>();
        Path d = ROOT.resolve("instances");
        if (Files.isDirectory(d)) {
            try (Stream<Path> s = Files.list(d)) {
                for (Path p : s.collect(Collectors.toList())) { Inst i = Inst.load(p); if (i != null) l.add(i); }
            } catch (IOException ignored) { }
        }
        l.sort(Comparator.comparing(i -> i.name.toLowerCase()));
        return l;
    }

    static String newId(String name) {
        String slug = name.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
        if (slug.isEmpty()) slug = "instance";
        String id = slug;
        for (int n = 2; Files.exists(ROOT.resolve("instances").resolve(id)); n++) id = slug + "-" + n;
        return id;
    }

    static String osName() {
        String o = System.getProperty("os.name").toLowerCase();
        return o.contains("win") ? "windows" : o.contains("mac") ? "osx" : "linux";
    }
    static String archName() {
        String a = System.getProperty("os.arch").toLowerCase();
        return a.contains("aarch64") || a.contains("arm64") ? "arm64" : a.contains("64") ? "x64" : "x86";
    }

    static boolean ruleApplies(Map<String, Object> r) {
        if (r.get("features") != null) return false;
        Map<String, Object> os = M(r.get("os"));
        if (os.get("name") != null && !os.get("name").equals(osName())) return false;
        if (os.get("arch") != null && !os.get("arch").equals(archName()) && !(os.get("arch").equals("x86") && archName().equals("x86"))) return false;
        return true;
    }
    static boolean rulesOk(Object rules) {
        List<Object> l = L(rules);
        if (l.isEmpty()) return true;
        boolean ok = false;
        for (Object o : l) {
            Map<String, Object> r = M(o);
            if (ruleApplies(r)) ok = "allow".equals(r.get("action"));
        }
        return ok;
    }

    static Map<String, Object> manifest() throws IOException {
        Path c = ROOT.resolve("versions").resolve("version_manifest.json");
        try {
            String b = get("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json");
            Files.createDirectories(c.getParent());
            Files.writeString(c, b);
            return M(Json.parse(b));
        } catch (IOException e) {
            if (Files.exists(c)) return M(Json.parse(Files.readString(c)));
            throw e;
        }
    }

    static Map<String, Object> vanillaJson(String mc, Consumer<String> log) throws IOException {
        Path f = ROOT.resolve("versions").resolve(mc).resolve(mc + ".json");
        if (Files.exists(f)) return M(Json.parse(Files.readString(f)));
        log.accept("Fetching metadata for Minecraft " + mc);
        for (Object v : L(manifest().get("versions"))) {
            if (mc.equals(S(at(v, "id")))) {
                String body = get(S(at(v, "url")));
                Files.createDirectories(f.getParent());
                Files.writeString(f, body);
                return M(Json.parse(body));
            }
        }
        throw new IOException("Unknown Minecraft version: " + mc);
    }

    static String loaderBase(String l) {
        return l.equals("quilt") ? "https://meta.quiltmc.org/v3/versions/loader/" : "https://meta.fabricmc.net/v2/versions/loader/";
    }
    static String latestLoader(String loader, String mc) throws IOException {
        List<Object> l = L(Json.parse(get(loaderBase(loader) + enc(mc))));
        for (Object o : l) {
            String v = S(at(o, "loader", "version"));
            Object st = at(o, "loader", "stable");
            if (v != null && (Boolean.TRUE.equals(st) || (st == null && !v.contains("-")))) return v;
        }
        if (!l.isEmpty()) return S(at(l.get(0), "loader", "version"));
        throw new IOException(cap(loader) + " is not available for Minecraft " + mc);
    }
    static Map<String, Object> loaderProfile(String loader, String mc, String ver) throws IOException {
        Path f = ROOT.resolve("versions").resolve(mc + "-" + loader + "-" + ver).resolve("profile.json");
        if (Files.exists(f)) return M(Json.parse(Files.readString(f)));
        String b = get(loaderBase(loader) + enc(mc) + "/" + enc(ver) + "/profile/json");
        Files.createDirectories(f.getParent());
        Files.writeString(f, b);
        return M(Json.parse(b));
    }

    static Map<String, Object> merge(Map<String, Object> p, Map<String, Object> c) {
        Map<String, Object> r = new LinkedHashMap<>(p);
        if (c.get("mainClass") != null) r.put("mainClass", c.get("mainClass"));
        List<Object> libs = new ArrayList<>(L(c.get("libraries")));
        libs.addAll(L(p.get("libraries")));
        r.put("libraries", libs);
        if (p.get("arguments") != null) {
            Map<String, Object> a = new LinkedHashMap<>();
            for (String k : new String[]{"jvm", "game"}) {
                List<Object> x = new ArrayList<>(L(at(p, "arguments", k)));
                x.addAll(L(at(c, "arguments", k)));
                a.put(k, x);
            }
            r.put("arguments", a);
        }
        return r;
    }

    static String mavenPath(String name) {
        String[] p = name.split("@")[0].split(":");
        String g = p[0].replace('.', '/'), a = p[1], v = p[2];
        return g + "/" + a + "/" + v + "/" + a + "-" + v + (p.length > 3 ? "-" + p[3] : "") + ".jar";
    }

    static void collect(List<Object> src, List<String> out) {
        for (Object o : src) {
            if (o instanceof String) out.add((String) o);
            else {
                Map<String, Object> m = M(o);
                if (!rulesOk(m.get("rules"))) continue;
                Object v = m.get("value");
                if (v instanceof List) for (Object x : (List<?>) v) out.add(String.valueOf(x));
                else if (v != null) out.add(String.valueOf(v));
            }
        }
    }

    static String expand(String s, Map<String, String> vars) {
        Matcher m = Pattern.compile("\\$\\{([^}]+)}").matcher(s);
        StringBuilder b = new StringBuilder();
        while (m.find()) {
            String v = vars.get(m.group(1));
            m.appendReplacement(b, Matcher.quoteReplacement(v == null ? m.group() : v));
        }
        m.appendTail(b);
        return b.toString();
    }

    static String cleanName(String n) {
        n = n == null ? "" : n.replaceAll("[^A-Za-z0-9_]", "");
        if (n.length() > 16) n = n.substring(0, 16);
        return n.isEmpty() ? "Player" : n;
    }

    static List<String> prepare(Inst in, String user, Consumer<String> log, Prog prog) throws Exception {
        Map<String, Object> v = vanillaJson(in.mc, log);
        Map<String, Object> prof = v;
        if (!in.loader.equals("vanilla")) {
            if (in.loaderVer.isEmpty()) { in.loaderVer = latestLoader(in.loader, in.mc); in.save(); }
            log.accept("Using " + cap(in.loader) + " " + in.loaderVer);
            prof = merge(v, loaderProfile(in.loader, in.mc, in.loaderVer));
        }
        Path libDir = ROOT.resolve("libraries"), natDir = in.dir().resolve("natives");
        Path clientJar = ROOT.resolve("versions").resolve(in.mc).resolve(in.mc + ".jar");
        List<Callable<Void>> tasks = new ArrayList<>();
        List<Path> cp = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        List<Path> natJars = new ArrayList<>();
        List<List<String>> natExcl = new ArrayList<>();

        Map<String, Object> cl = M(at(v, "downloads", "client"));
        tasks.add(() -> { download(S(cl.get("url")), clientJar, S(cl.get("sha1")), "SHA-1", N(cl.get("size"), -1)); return null; });

        for (Object lo : L(prof.get("libraries"))) {
            Map<String, Object> lib = M(lo);
            if (!rulesOk(lib.get("rules"))) continue;
            Map<String, Object> art = M(at(lib, "downloads", "artifact"));
            String path = null, url = null, sha = null; long size = -1;
            if (!art.isEmpty() && art.get("url") != null) {
                path = S(art.get("path")); url = S(art.get("url")); sha = S(art.get("sha1")); size = N(art.get("size"), -1);
            } else if (lib.get("name") != null && lib.get("natives") == null) {
                path = mavenPath(S(lib.get("name")));
                String base = lib.get("url") == null ? "https://libraries.minecraft.net/" : S(lib.get("url"));
                url = (base.endsWith("/") ? base : base + "/") + path;
                sha = S(lib.get("sha1"));
            }
            if (path != null && seen.add(path)) {
                Path dest = libDir.resolve(path);
                final String fu = url, fs = sha; final long fz = size;
                tasks.add(() -> { download(fu, dest, fs, "SHA-1", fz); return null; });
                cp.add(dest);
            }
            Map<String, Object> nat = M(lib.get("natives"));
            if (nat.get(osName()) != null) {
                String cls = S(nat.get(osName())).replace("${arch}", System.getProperty("os.arch").contains("64") ? "64" : "32");
                Map<String, Object> n = M(at(lib, "downloads", "classifiers", cls));
                if (n.get("url") != null) {
                    Path dest = libDir.resolve(S(n.get("path")));
                    tasks.add(() -> { download(S(n.get("url")), dest, S(n.get("sha1")), "SHA-1", N(n.get("size"), -1)); return null; });
                    natJars.add(dest);
                    List<String> ex = new ArrayList<>();
                    for (Object e : L(at(lib, "extract", "exclude"))) ex.add(String.valueOf(e));
                    natExcl.add(ex);
                }
            }
        }

        Map<String, Object> ai = M(prof.get("assetIndex"));
        String aid = S(ai.get("id"));
        Path assets = ROOT.resolve("assets"), idxFile = assets.resolve("indexes").resolve(aid + ".json");
        download(S(ai.get("url")), idxFile, S(ai.get("sha1")), "SHA-1", N(ai.get("size"), -1));
        Map<String, Object> objs = M(at(Json.parse(Files.readString(idxFile)), "objects"));
        boolean virtual = Boolean.TRUE.equals(at(Json.parse(Files.readString(idxFile)), "virtual"));
        for (Map.Entry<String, Object> e : objs.entrySet()) {
            String h = S(at(e.getValue(), "hash"));
            long sz = N(at(e.getValue(), "size"), -1);
            String pre = h.substring(0, 2);
            Path dest = assets.resolve("objects").resolve(pre).resolve(h);
            tasks.add(() -> { download("https://resources.download.minecraft.net/" + pre + "/" + h, dest, h, "SHA-1", sz); return null; });
        }
        log.accept("Checking " + tasks.size() + " files");
        runAll(tasks, prog, "Downloading");

        String gameAssets = assets.toString();
        if (virtual) {
            Path vdir = assets.resolve("virtual").resolve(aid);
            for (Map.Entry<String, Object> e : objs.entrySet()) {
                String h = S(at(e.getValue(), "hash"));
                Path to = safe(vdir, e.getKey());
                if (!Files.exists(to)) {
                    Files.createDirectories(to.getParent());
                    Files.copy(assets.resolve("objects").resolve(h.substring(0, 2)).resolve(h), to);
                }
            }
            gameAssets = vdir.toString();
        }

        deleteTree(natDir);
        Files.createDirectories(natDir);
        for (int i = 0; i < natJars.size(); i++) {
            try (InputStream is = Files.newInputStream(natJars.get(i))) { unzip(is, natDir, natExcl.get(i)); }
        }

        List<String> all = new ArrayList<>();
        for (Path p : cp) all.add(p.toString());
        all.add(clientJar.toString());
        Map<String, String> vars = new HashMap<>();
        vars.put("auth_player_name", user);
        vars.put("auth_uuid", UUID.nameUUIDFromBytes(("OfflinePlayer:" + user).getBytes(StandardCharsets.UTF_8)).toString().replace("-", ""));
        vars.put("auth_access_token", "0"); vars.put("auth_session", "0"); vars.put("auth_xuid", "0"); vars.put("clientid", "0");
        vars.put("user_type", "legacy"); vars.put("user_properties", "{}");
        vars.put("version_name", in.mc); vars.put("version_type", "release");
        vars.put("game_directory", in.game().toString());
        vars.put("assets_root", assets.toString()); vars.put("game_assets", gameAssets); vars.put("assets_index_name", aid);
        vars.put("natives_directory", natDir.toString());
        vars.put("launcher_name", "Skeleton"); vars.put("launcher_version", VERSION);
        vars.put("classpath", String.join(File.pathSeparator, all)); vars.put("classpath_separator", File.pathSeparator);
        vars.put("library_directory", libDir.toString());

        List<String> jvm = new ArrayList<>(), game = new ArrayList<>();
        collect(L(at(prof, "arguments", "jvm")), jvm);
        collect(L(at(prof, "arguments", "game")), game);
        if (prof.get("minecraftArguments") != null) game.addAll(Arrays.asList(S(prof.get("minecraftArguments")).split(" +")));
        if (!jvm.contains("-cp") && !jvm.contains("-classpath")) { jvm.add("-cp"); jvm.add("${classpath}"); }
        if (jvm.stream().noneMatch(s -> s.startsWith("-Djava.library.path"))) jvm.add(0, "-Djava.library.path=${natives_directory}");

        String exe = in.java.isBlank()
                ? Paths.get(System.getProperty("java.home"), "bin", osName().equals("windows") ? "java.exe" : "java").toString()
                : in.java.trim();
        long need = N(at(prof, "javaVersion", "majorVersion"), 0);
        if (in.java.isBlank() && need > 0 && need != Runtime.version().feature())
            log.accept("Warning: Minecraft " + in.mc + " is built for Java " + need + " but Skeleton runs on Java "
                    + Runtime.version().feature() + ". Set a Java path in Settings if the game fails to start.");

        List<String> cmd = new ArrayList<>();
        cmd.add(exe);
        cmd.add("-Xmx" + in.ram + "M");
        cmd.add("-Xms" + Math.min(1024, in.ram) + "M");
        if (in.opt) cmd.addAll(Arrays.asList(AIKAR.split(" ")));
        for (String s : in.jvm.trim().split("\\s+")) if (!s.isEmpty()) cmd.add(s);
        for (String s : jvm) cmd.add(expand(s, vars));
        cmd.add(S(prof.get("mainClass")));
        for (String s : game) cmd.add(expand(s, vars));
        return cmd;
    }

    static List<String> loadersFor(Inst in) {
        if (in.loader.equals("quilt")) return List.of("quilt", "fabric");
        if (in.loader.equals("vanilla")) return List.of();
        return List.of(in.loader);
    }

    static List<Object> search(String q, String type, Inst in) throws IOException {
        StringBuilder f = new StringBuilder("[[\"project_type:" + type + "\"]");
        if (in != null && type.equals("mod")) {
            f.append(",[\"versions:").append(in.mc).append("\"]");
            List<String> ld = loadersFor(in);
            if (!ld.isEmpty()) f.append(",[").append(ld.stream().map(x -> "\"categories:" + x + "\"").collect(Collectors.joining(","))).append("]");
        }
        f.append("]");
        String url = API + "/search?query=" + enc(q) + "&limit=30&index=" + (q.isBlank() ? "downloads" : "relevance") + "&facets=" + enc(f.toString());
        return L(at(Json.parse(get(url)), "hits"));
    }

    static Map<String, Object> bestVersion(String project, Inst in) throws IOException {
        String url = API + "/project/" + enc(project) + "/version";
        if (in != null) {
            url += "?game_versions=" + enc("[\"" + in.mc + "\"]");
            List<String> ld = loadersFor(in);
            if (!ld.isEmpty()) url += "&loaders=" + enc(jsonArr(ld));
        }
        List<Object> vs = L(Json.parse(get(url)));
        for (Object v : vs) if ("release".equals(S(at(v, "version_type")))) return M(v);
        return vs.isEmpty() ? null : M(vs.get(0));
    }

    static Map<String, Object> primaryFile(Map<String, Object> v) {
        List<Object> fs = L(v.get("files"));
        for (Object f : fs) if (Boolean.TRUE.equals(M(f).get("primary"))) return M(f);
        return fs.isEmpty() ? null : M(fs.get(0));
    }

    static void installMod(String project, Inst in, Set<String> seen, Consumer<String> log) throws Exception {
        if (!seen.add(project)) return;
        Map<String, Object> v = bestVersion(project, in);
        if (v == null) throw new IOException("No version of '" + project + "' for Minecraft " + in.mc + " (" + in.loader + ")");
        Map<String, Object> f = primaryFile(v);
        if (f == null) throw new IOException("'" + project + "' has no downloadable file");
        log.accept("Installing " + f.get("filename"));
        download(S(f.get("url")), safe(in.game().resolve("mods"), S(f.get("filename"))), S(at(f, "hashes", "sha512")), "SHA-512", -1);
        for (Object d : L(v.get("dependencies")))
            if ("required".equals(S(at(d, "dependency_type"))) && at(d, "project_id") != null)
                installMod(S(at(d, "project_id")), in, seen, log);
    }

    static Path downloadPack(String project, Consumer<String> log) throws IOException {
        Map<String, Object> v = bestVersion(project, null);
        if (v == null) throw new IOException("This modpack has no versions");
        Map<String, Object> f = primaryFile(v);
        log.accept("Downloading " + f.get("filename"));
        Path dest = safe(ROOT.resolve("cache"), S(f.get("filename")));
        download(S(f.get("url")), dest, S(at(f, "hashes", "sha512")), "SHA-512", -1);
        return dest;
    }

    static Inst installPack(Path file, Prog prog, Consumer<String> log) throws Exception {
        try (ZipFile z = new ZipFile(file.toFile())) {
            ZipEntry ie = z.getEntry("modrinth.index.json");
            if (ie == null) throw new IOException("This is not a Modrinth pack (modrinth.index.json is missing).");
            Map<String, Object> idx = M(Json.parse(new String(z.getInputStream(ie).readAllBytes(), StandardCharsets.UTF_8)));
            Map<String, Object> deps = M(idx.get("dependencies"));
            String mc = S(deps.get("minecraft"));
            if (mc == null) throw new IOException("The pack does not name a Minecraft version.");
            Inst in = new Inst();
            in.mc = mc;
            if (deps.containsKey("fabric-loader")) { in.loader = "fabric"; in.loaderVer = S(deps.get("fabric-loader")); }
            else if (deps.containsKey("quilt-loader")) { in.loader = "quilt"; in.loaderVer = S(deps.get("quilt-loader")); }
            else if (deps.containsKey("forge") || deps.containsKey("neoforge"))
                throw new IOException("This pack needs " + (deps.containsKey("forge") ? "Forge" : "NeoForge")
                        + ", which Skeleton can't install yet. Fabric and Quilt packs work.");
            in.name = idx.get("name") == null ? "Modpack" : S(idx.get("name"));
            in.id = newId(in.name);
            in.save();
            Path game = in.game();
            List<Callable<Void>> tasks = new ArrayList<>();
            for (Object fo : L(idx.get("files"))) {
                Map<String, Object> f = M(fo);
                if ("unsupported".equals(S(at(f, "env", "client")))) continue;
                Path dest = safe(game, S(f.get("path")));
                String url = S(at(f, "downloads", 0));
                if (url == null) continue;
                String sha = S(at(f, "hashes", "sha512"));
                tasks.add(() -> { download(url, dest, sha, "SHA-512", -1); return null; });
            }
            log.accept("Downloading " + tasks.size() + " mods and files for " + in.name);
            runAll(tasks, prog, "Downloading pack");
            for (String prefix : new String[]{"overrides/", "client-overrides/"}) {
                for (Enumeration<? extends ZipEntry> en = z.entries(); en.hasMoreElements(); ) {
                    ZipEntry e = en.nextElement();
                    if (e.isDirectory() || !e.getName().startsWith(prefix)) continue;
                    Path out = safe(game, e.getName().substring(prefix.length()));
                    Files.createDirectories(out.getParent());
                    try (InputStream is = z.getInputStream(e)) { Files.copy(is, out, StandardCopyOption.REPLACE_EXISTING); }
                }
            }
            return in;
        }
    }

    static final Color BG = new Color(0x17181b), SIDE = new Color(0x1d1f23), CARD = new Color(0x25272c), LINE = new Color(0x373a41),
            TEXT = new Color(0xe6e3da), MUTED = new Color(0x8e9097), BONE = new Color(0xd9d4c5), BONE_HI = new Color(0xece8dc),
            INK = new Color(0x1a1b1e), RED = new Color(0xd0706a);

    static Font F(int style, float size) { return new Font(Font.SANS_SERIF, style, 13).deriveFont(style, size); }
    static void aa(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    }

    static class Btn extends JButton {
        final int kind;
        Btn(String t, int kind) {
            super(t);
            this.kind = kind;
            setContentAreaFilled(false); setBorderPainted(false); setFocusPainted(false); setOpaque(false);
            setRolloverEnabled(true); setFont(F(Font.BOLD, 13));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }
        public Dimension getPreferredSize() {
            if (isPreferredSizeSet()) return super.getPreferredSize();
            return new Dimension(getFontMetrics(getFont()).stringWidth(getText()) + (kind == 3 ? 20 : 34), 36);
        }
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            aa(g);
            ButtonModel m = getModel();
            Color bg = null, fg, bd = null;
            if (kind == 0) { bg = m.isPressed() ? BONE.darker() : m.isRollover() ? BONE_HI : BONE; fg = INK; }
            else if (kind == 1) { bg = m.isRollover() ? LINE : CARD; fg = TEXT; bd = LINE; }
            else { bg = m.isRollover() ? new Color(0x3a2626) : null; fg = RED; }
            if (!isEnabled()) { fg = MUTED; if (kind == 0) bg = LINE; }
            if (bg != null) { g.setColor(bg); g.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10); }
            if (bd != null) { g.setColor(bd); g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10); }
            g.setColor(fg); g.setFont(getFont());
            FontMetrics fm = g.getFontMetrics();
            g.drawString(getText(), (getWidth() - fm.stringWidth(getText())) / 2, (getHeight() + fm.getAscent() - fm.getDescent()) / 2);
            g.dispose();
        }
    }

    static class Tab extends JToggleButton {
        Tab(String t) {
            super(t);
            setContentAreaFilled(false); setBorderPainted(false); setFocusPainted(false); setOpaque(false);
            setRolloverEnabled(true); setFont(F(Font.PLAIN, 14));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }
        public Dimension getPreferredSize() { return new Dimension(getFontMetrics(getFont()).stringWidth(getText()) + 32, 40); }
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            aa(g);
            g.setFont(getFont());
            g.setColor(isSelected() || getModel().isRollover() ? TEXT : MUTED);
            FontMetrics fm = g.getFontMetrics();
            g.drawString(getText(), 16, (getHeight() + fm.getAscent() - fm.getDescent()) / 2 - 1);
            if (isSelected()) { g.setColor(BONE); g.fillRect(16, getHeight() - 2, getWidth() - 32, 2); }
            g.dispose();
        }
    }

    static class Chk extends JCheckBox {
        Chk(String t, boolean on) {
            super(t, on);
            setOpaque(false); setForeground(TEXT); setFont(F(Font.PLAIN, 13)); setFocusPainted(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setIcon(new Icon() {
                public int getIconWidth() { return 20; }
                public int getIconHeight() { return 18; }
                public void paintIcon(Component c, Graphics g0, int x, int y) {
                    Graphics2D g = (Graphics2D) g0.create(); aa(g);
                    boolean s = ((AbstractButton) c).isSelected();
                    g.setColor(s ? BONE : CARD); g.fillRoundRect(x, y + 1, 16, 16, 5, 5);
                    g.setColor(s ? BONE : LINE); g.drawRoundRect(x, y + 1, 15, 15, 5, 5);
                    if (s) { g.setColor(INK); g.setStroke(new BasicStroke(2f)); g.drawLine(x + 4, y + 9, x + 7, y + 12); g.drawLine(x + 7, y + 12, x + 12, y + 6); }
                    g.dispose();
                }
            });
        }
    }

    static class Bar extends JComponent {
        double v;
        void set(double v) { this.v = Math.max(0, Math.min(1, v)); repaint(); }
        public Dimension getPreferredSize() { return new Dimension(100, 6); }
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create(); aa(g);
            g.setColor(CARD); g.fillRoundRect(0, 0, getWidth(), getHeight(), 6, 6);
            g.setColor(BONE); g.fillRoundRect(0, 0, (int) (getWidth() * v), getHeight(), 6, 6);
            g.dispose();
        }
    }

    static class SlimBar extends BasicScrollBarUI {
        protected void configureScrollBarColors() { thumbColor = LINE; trackColor = CARD; }
        JButton zero() { JButton b = new JButton(); b.setPreferredSize(new Dimension(0, 0)); return b; }
        protected JButton createDecreaseButton(int o) { return zero(); }
        protected JButton createIncreaseButton(int o) { return zero(); }
        protected void paintTrack(Graphics g, JComponent c, Rectangle r) { g.setColor(CARD); g.fillRect(r.x, r.y, r.width, r.height); }
        protected void paintThumb(Graphics g0, JComponent c, Rectangle r) {
            if (r.isEmpty()) return;
            Graphics2D g = (Graphics2D) g0.create(); aa(g);
            g.setColor(isThumbRollover() ? MUTED : new Color(0x4a4d55));
            g.fillRoundRect(r.x + 2, r.y + 2, r.width - 4, r.height - 4, 6, 6);
            g.dispose();
        }
    }

    static class Row extends JComponent {
        final String[] t; final Font[] f; final Color[] c; final Color bg; final boolean bar; final int[] y;
        Row(String[] t, Font[] f, Color[] c, int[] y, Color bg, boolean bar) { this.t = t; this.f = f; this.c = c; this.y = y; this.bg = bg; this.bar = bar; }
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create(); aa(g);
            g.setColor(bg); g.fillRect(0, 0, getWidth(), getHeight());
            if (bar) { g.setColor(BONE); g.fillRect(0, 10, 3, getHeight() - 20); }
            int x = bar || getWidth() < 400 ? 18 : 14;
            for (int i = 0; i < t.length; i++) {
                g.setFont(f[i]); g.setColor(c[i]);
                String s = t[i] == null ? "" : t[i];
                FontMetrics fm = g.getFontMetrics();
                int max = getWidth() - x - 10;
                while (s.length() > 1 && fm.stringWidth(s) > max) s = s.substring(0, s.length() - 2) + "…";
                g.drawString(s, x, y[i]);
            }
            g.dispose();
        }
    }

    static JScrollPane scroll(Component c) {
        JScrollPane s = new JScrollPane(c);
        s.setBorder(new LineBorder(LINE));
        s.getViewport().setBackground(CARD);
        s.getVerticalScrollBar().setUI(new SlimBar());
        s.getVerticalScrollBar().setPreferredSize(new Dimension(10, 0));
        s.getHorizontalScrollBar().setUI(new SlimBar());
        s.getHorizontalScrollBar().setPreferredSize(new Dimension(0, 10));
        return s;
    }

    static <T extends JTextComponent> T style(T t) {
        t.setBackground(CARD); t.setForeground(TEXT); t.setCaretColor(TEXT); t.setFont(F(Font.PLAIN, 13));
        t.setSelectionColor(new Color(0x5a584f)); t.setSelectedTextColor(TEXT);
        return t;
    }

    static JTextField field(String placeholder) {
        JTextField t = new JTextField() {
            protected void paintComponent(Graphics g0) {
                super.paintComponent(g0);
                if (getText().isEmpty() && placeholder != null) {
                    Graphics2D g = (Graphics2D) g0.create(); aa(g);
                    g.setColor(MUTED); g.setFont(getFont());
                    Insets in = getInsets();
                    g.drawString(placeholder, in.left, (getHeight() + g.getFontMetrics().getAscent() - g.getFontMetrics().getDescent()) / 2);
                    g.dispose();
                }
            }
        };
        style(t);
        t.setBorder(new CompoundBorder(new LineBorder(LINE), new EmptyBorder(7, 10, 7, 10)));
        t.setPreferredSize(new Dimension(200, 36));
        return t;
    }

    static <T> JComboBox<T> combo() {
        JComboBox<T> c = new JComboBox<>();
        c.setUI(new BasicComboBoxUI() {
            public void paintCurrentValueBackground(Graphics g, Rectangle b, boolean focus) {
                g.setColor(CARD); g.fillRect(b.x, b.y, b.width, b.height);
            }
            protected JButton createArrowButton() {
                JButton b = new JButton() {
                    protected void paintComponent(Graphics g0) {
                        Graphics2D g = (Graphics2D) g0.create(); aa(g);
                        g.setColor(CARD); g.fillRect(0, 0, getWidth(), getHeight());
                        g.setColor(MUTED); g.setStroke(new BasicStroke(1.6f));
                        int cx = getWidth() / 2, cy = getHeight() / 2;
                        g.drawLine(cx - 4, cy - 2, cx, cy + 2); g.drawLine(cx, cy + 2, cx + 4, cy - 2);
                        g.dispose();
                    }
                };
                b.setBorder(null); b.setFocusable(false);
                return b;
            }
        });
        c.setBackground(CARD); c.setForeground(TEXT); c.setFont(F(Font.PLAIN, 13)); c.setBorder(new LineBorder(LINE));
        c.setRenderer(new DefaultListCellRenderer() {
            public Component getListCellRendererComponent(JList<?> l, Object v, int i, boolean sel, boolean f) {
                JLabel r = (JLabel) super.getListCellRendererComponent(l, v, i, sel, f);
                r.setBorder(new EmptyBorder(6, 10, 6, 10));
                r.setBackground(sel && i >= 0 ? LINE : CARD); r.setForeground(TEXT); r.setOpaque(true);
                return r;
            }
        });
        c.setPreferredSize(new Dimension(180, 36));
        return c;
    }

    static JLabel label(String t, int style, float size, Color c) {
        JLabel l = new JLabel(t); l.setFont(F(style, size)); l.setForeground(c); return l;
    }
    static JPanel panel(LayoutManager lm, Color bg) {
        JPanel p = new JPanel(lm); p.setBackground(bg); p.setOpaque(bg != null); return p;
    }

    static BufferedImage ICON;
    static BufferedImage icon() {
        if (ICON != null) return ICON;
        try (InputStream in = Skeleton.class.getResourceAsStream("/icon.jpg")) {
            if (in != null) ICON = ImageIO.read(in);
        } catch (Exception ignored) { }
        if (ICON == null) {
            ICON = new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = ICON.createGraphics(); g.setColor(BONE); g.fillRect(0, 0, 8, 8); g.setColor(INK);
            g.fillRect(1, 3, 2, 1); g.fillRect(5, 3, 2, 1); g.fillRect(1, 6, 6, 1); g.dispose();
        }
        return ICON;
    }

    interface Job { void run() throws Exception; }

    static class App {
        JFrame frame;
        JComponent root;
        final DefaultListModel<Inst> instModel = new DefaultListModel<>();
        final JList<Inst> instList = new JList<>(instModel);
        final JTextField user = field("Player name");
        final JLabel title = label("Skeleton", Font.BOLD, 22, TEXT), sub = label(" ", Font.PLAIN, 13, MUTED);
        final JLabel status = label("Ready", Font.PLAIN, 12, MUTED);
        final Bar bar = new Bar();
        final CardLayout cards = new CardLayout();
        final JPanel content = panel(cards, BG);
        final JTextArea log = style(new JTextArea());
        final Btn playBtn = new Btn("Play", 0);
        final DefaultListModel<Path> modModel = new DefaultListModel<>();
        final JList<Path> modList = new JList<>(modModel);
        final JLabel modCount = label("", Font.PLAIN, 13, MUTED);
        final DefaultListModel<Object> hitModel = new DefaultListModel<>();
        final JList<Object> hitList = new JList<>(hitModel);
        final JTextField q = field("Search Modrinth");
        final JComboBox<String> qType = combo();
        final Chk qMatch = new Chk("Match this instance", true);
        final JLabel browseNote = label(" ", Font.PLAIN, 12, MUTED);
        final JTextField ram = field(null), javaPath = field("Default (the Java running Skeleton)"), jvmArgs = field("Extra JVM flags");
        final Chk optFlags = new Chk("Use tuned JVM flags (G1GC, lower pause times)", true);
        final AtomicBoolean busy = new AtomicBoolean();
        volatile Process proc;
        boolean loading;

        App() {
            theme();
            log.setEditable(false); log.setLineWrap(true); log.setWrapStyleWord(true);
            log.setBorder(new EmptyBorder(10, 12, 10, 12)); log.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
            root = buildRoot();
            reload(null);
            Map<String, Object> st = settings();
            user.setText(st.get("username") == null ? "" : S(st.get("username")));
        }

        static void theme() {
            UIManager.put("ToolTip.background", CARD); UIManager.put("ToolTip.foreground", TEXT);
            UIManager.put("ToolTip.border", new LineBorder(LINE));
            UIManager.put("PopupMenu.border", new LineBorder(LINE));
            UIManager.put("Panel.background", BG);
        }

        Map<String, Object> settings() {
            try { return M(Json.parse(Files.readString(ROOT.resolve("settings.json")))); } catch (Exception e) { return new LinkedHashMap<>(); }
        }
        void saveSettings() {
            try {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("username", user.getText().trim());
                Inst i = cur();
                if (i != null) m.put("last", i.id);
                Files.createDirectories(ROOT);
                Files.writeString(ROOT.resolve("settings.json"), Json.write(m));
            } catch (IOException ignored) { }
        }

        Inst cur() { return instList.getSelectedValue(); }
        void ui(Runnable r) { SwingUtilities.invokeLater(r); }

        void logLine(String s) {
            ui(() -> {
                log.append(s + "\n");
                if (log.getDocument().getLength() > 200_000) {
                    try { log.getDocument().remove(0, 100_000); } catch (Exception ignored) { }
                }
                log.setCaretPosition(log.getDocument().getLength());
                status.setText(s.length() > 140 ? s.substring(0, 140) + "…" : s);
            });
        }
        void progress(String label, int d, int t) {
            ui(() -> { bar.set(t == 0 ? 0 : (double) d / t); status.setText(label + " " + d + " of " + t); });
        }
        static String msg(Throwable e) { return e.getMessage() == null ? e.toString() : e.getMessage(); }

        void task(Job j) {
            if (!busy.compareAndSet(false, true)) { logLine("Wait for the current task to finish."); return; }
            ui(() -> status.setText("Working…"));
            Thread t = new Thread(() -> {
                try { j.run(); }
                catch (Exception e) { logLine("Error: " + msg(e)); ui(() -> message("Something went wrong", msg(e))); }
                finally { busy.set(false); ui(() -> { bar.set(0); refreshMods(); }); }
            }, "skeleton-task");
            t.setDaemon(true);
            t.start();
        }

        boolean ask(String head, String body, String ok, boolean cancel) {
            JDialog d = new JDialog(frame, head, true);
            boolean[] res = {false};
            JPanel p = panel(new BorderLayout(0, 16), BG);
            p.setBorder(new EmptyBorder(20, 22, 18, 22));
            JTextArea t = new JTextArea(body);
            t.setEditable(false); t.setLineWrap(true); t.setWrapStyleWord(true); t.setOpaque(false); t.setForeground(TEXT); t.setFont(F(Font.PLAIN, 13));
            t.setPreferredSize(new Dimension(380, 20 + 17 * (body.length() / 48 + 1)));
            p.add(label(head, Font.BOLD, 16, TEXT), BorderLayout.NORTH);
            p.add(t, BorderLayout.CENTER);
            JPanel b = panel(new FlowLayout(FlowLayout.RIGHT, 8, 0), null);
            if (cancel) { Btn c = new Btn("Cancel", 1); c.addActionListener(e -> d.dispose()); b.add(c); }
            Btn o = new Btn(ok, cancel ? 3 : 0);
            o.addActionListener(e -> { res[0] = true; d.dispose(); });
            b.add(o);
            p.add(b, BorderLayout.SOUTH);
            d.setContentPane(p); d.pack(); d.setLocationRelativeTo(frame); d.setVisible(true);
            return res[0];
        }
        void message(String head, String body) { ask(head, body, "OK", false); }

        JComponent buildRoot() {
            JPanel r = panel(new BorderLayout(), BG);
            r.add(buildSidebar(), BorderLayout.WEST);
            r.add(buildMain(), BorderLayout.CENTER);
            return r;
        }

        JComponent buildSidebar() {
            JPanel s = panel(new BorderLayout(), SIDE);
            s.setPreferredSize(new Dimension(250, 0));
            s.setBorder(new MatteBorder(0, 0, 0, 1, LINE));
            JPanel head = panel(new BorderLayout(12, 0), null);
            head.setBorder(new EmptyBorder(20, 18, 16, 18));
            JComponent logo = new JComponent() {
                public Dimension getPreferredSize() { return new Dimension(40, 40); }
                protected void paintComponent(Graphics g0) {
                    Graphics2D g = (Graphics2D) g0.create();
                    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                    g.drawImage(icon(), 0, 0, 40, 40, null);
                    g.dispose();
                }
            };
            head.add(logo, BorderLayout.WEST);
            JPanel names = panel(new GridLayout(2, 1), null);
            names.add(label("Skeleton", Font.BOLD, 20, TEXT));
            names.add(label("Launcher " + VERSION, Font.PLAIN, 12, MUTED));
            head.add(names, BorderLayout.CENTER);
            s.add(head, BorderLayout.NORTH);

            instList.setBackground(SIDE); instList.setFixedCellHeight(56);
            instList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            instList.setCellRenderer((l, v, i, sel, f) -> new Row(new String[]{v.name, v.sub()},
                    new Font[]{F(Font.BOLD, 14), F(Font.PLAIN, 12)}, new Color[]{sel ? TEXT : new Color(0xc9c6bd), MUTED},
                    new int[]{25, 43}, sel ? CARD : SIDE, sel));
            instList.addListSelectionListener(e -> { if (!e.getValueIsAdjusting() && !loading) show(cur()); });
            JScrollPane sp = scroll(instList);
            sp.setBorder(null); sp.getViewport().setBackground(SIDE);
            s.add(sp, BorderLayout.CENTER);

            JPanel foot = panel(new GridBagLayout(), null);
            foot.setBorder(new EmptyBorder(14, 18, 18, 18));
            GridBagConstraints c = new GridBagConstraints();
            c.gridx = 0; c.fill = GridBagConstraints.HORIZONTAL; c.weightx = 1; c.insets = new Insets(0, 0, 8, 0);
            c.gridy = 0; foot.add(label("Player name", Font.PLAIN, 12, MUTED), c);
            c.gridy = 1; c.insets = new Insets(0, 0, 14, 0); foot.add(user, c);
            Btn nw = new Btn("New instance", 0); nw.addActionListener(e -> newInstanceDialog());
            Btn imp = new Btn("Import .mrpack", 1); imp.addActionListener(e -> importFile());
            c.insets = new Insets(0, 0, 8, 0);
            c.gridy = 2; foot.add(nw, c);
            c.gridy = 3; c.insets = new Insets(0, 0, 0, 0); foot.add(imp, c);
            s.add(foot, BorderLayout.SOUTH);
            return s;
        }

        JComponent buildMain() {
            JPanel m = panel(new BorderLayout(), BG);
            JPanel head = panel(new BorderLayout(), null);
            head.setBorder(new EmptyBorder(22, 28, 0, 28));
            JPanel tp = panel(new GridLayout(2, 1, 0, 2), null);
            tp.add(title); tp.add(sub);
            head.add(tp, BorderLayout.NORTH);
            JPanel tabs = panel(new FlowLayout(FlowLayout.LEFT, 0, 0), null);
            ButtonGroup g = new ButtonGroup();
            String[][] names = {{"Play", "play"}, {"Mods", "mods"}, {"Browse Modrinth", "browse"}, {"Settings", "settings"}};
            for (String[] n : names) {
                Tab t = new Tab(n[0]);
                t.setActionCommand(n[1]);
                t.addActionListener(e -> cards.show(content, n[1]));
                g.add(t); tabs.add(t);
                if (n[1].equals("play")) t.setSelected(true);
            }
            tabsGroup = g;
            JPanel hb = panel(new BorderLayout(), null);
            hb.add(head, BorderLayout.NORTH);
            JPanel tabWrap = panel(new BorderLayout(), null);
            tabWrap.setBorder(new EmptyBorder(8, 12, 0, 12));
            tabWrap.add(tabs, BorderLayout.WEST);
            hb.add(tabWrap, BorderLayout.CENTER);
            hb.setBorder(new MatteBorder(0, 0, 1, 0, LINE));
            m.add(hb, BorderLayout.NORTH);

            content.add(playCard(), "play");
            content.add(modsCard(), "mods");
            content.add(browseCard(), "browse");
            content.add(settingsCard(), "settings");
            content.add(emptyCard(), "empty");
            content.setBorder(new EmptyBorder(20, 28, 8, 28));
            m.add(content, BorderLayout.CENTER);

            JPanel st = panel(new BorderLayout(0, 8), null);
            st.setBorder(new EmptyBorder(6, 28, 14, 28));
            st.add(bar, BorderLayout.NORTH); st.add(status, BorderLayout.CENTER);
            m.add(st, BorderLayout.SOUTH);
            return m;
        }
        ButtonGroup tabsGroup;

        JComponent emptyCard() {
            JPanel p = panel(new GridBagLayout(), null);
            JPanel in = panel(new GridLayout(0, 1, 0, 10), null);
            in.add(label("No instances yet", Font.BOLD, 18, TEXT));
            in.add(label("Create one with Fabric or Quilt, or import a Modrinth modpack.", Font.PLAIN, 13, MUTED));
            Btn b = new Btn("New instance", 0); b.addActionListener(e -> newInstanceDialog());
            JPanel row = panel(new FlowLayout(FlowLayout.LEFT, 0, 0), null); row.add(b);
            in.add(row);
            p.add(in);
            return p;
        }

        JComponent playCard() {
            JPanel p = panel(new BorderLayout(0, 16), null);
            JPanel top = panel(new FlowLayout(FlowLayout.LEFT, 0, 0), null);
            playBtn.setPreferredSize(new Dimension(170, 46)); playBtn.setFont(F(Font.BOLD, 15));
            playBtn.addActionListener(e -> play());
            top.add(playBtn);
            p.add(top, BorderLayout.NORTH);
            p.add(scroll(log), BorderLayout.CENTER);
            return p;
        }

        JComponent modsCard() {
            JPanel p = panel(new BorderLayout(0, 14), null);
            JPanel top = panel(new BorderLayout(), null);
            top.add(modCount, BorderLayout.WEST);
            JPanel btns = panel(new FlowLayout(FlowLayout.RIGHT, 8, 0), null);
            Btn perf = new Btn("Add performance mods", 0);
            perf.setToolTipText("Sodium, Lithium, FerriteCore, ModernFix, EntityCulling, ImmediatelyFast (Fabric/Quilt)");
            perf.addActionListener(e -> addPerf());
            Btn tog = new Btn("Enable or disable", 1); tog.addActionListener(e -> toggleMod());
            Btn rm = new Btn("Remove", 3); rm.addActionListener(e -> removeMod());
            Btn open = new Btn("Open folder", 1); open.addActionListener(e -> openFolder());
            btns.add(perf); btns.add(tog); btns.add(open); btns.add(rm);
            top.add(btns, BorderLayout.EAST);
            p.add(top, BorderLayout.NORTH);
            modList.setBackground(CARD); modList.setFixedCellHeight(34);
            modList.setCellRenderer((l, v, i, sel, f) -> {
                String n = v.getFileName().toString();
                boolean off = n.endsWith(".disabled");
                JLabel lb = label((off ? "Off   " : "On    ") + (off ? n.substring(0, n.length() - 9) : n), Font.PLAIN, 13, off ? MUTED : TEXT);
                lb.setOpaque(true); lb.setBackground(sel ? LINE : CARD); lb.setBorder(new EmptyBorder(0, 12, 0, 12));
                return lb;
            });
            p.add(scroll(modList), BorderLayout.CENTER);
            return p;
        }

        JComponent browseCard() {
            JPanel p = panel(new BorderLayout(0, 14), null);
            JPanel top = panel(new BorderLayout(10, 0), null);
            qType.addItem("Mods"); qType.addItem("Modpacks"); qType.setPreferredSize(new Dimension(130, 36));
            Btn go = new Btn("Search", 0);
            top.add(q, BorderLayout.CENTER);
            JPanel right = panel(new FlowLayout(FlowLayout.RIGHT, 10, 0), null);
            right.add(qType); right.add(go);
            top.add(right, BorderLayout.EAST);
            JPanel north = panel(new BorderLayout(0, 10), null);
            north.add(top, BorderLayout.NORTH);
            north.add(qMatch, BorderLayout.SOUTH);
            p.add(north, BorderLayout.NORTH);
            go.addActionListener(e -> doSearch());
            q.addActionListener(e -> doSearch());
            hitList.setBackground(CARD); hitList.setFixedCellHeight(70);
            hitList.setCellRenderer((l, v, i, sel, f) -> {
                String d = S(at(v, "description")); if (d == null) d = "";
                return new Row(new String[]{S(at(v, "title")), "by " + S(at(v, "author")) + ", " + count(N(at(v, "downloads"), 0)) + " downloads", d},
                        new Font[]{F(Font.BOLD, 14), F(Font.PLAIN, 12), F(Font.PLAIN, 12)}, new Color[]{TEXT, MUTED, new Color(0xb4b2aa)},
                        new int[]{24, 43, 61}, sel ? LINE : CARD, false);
            });
            p.add(scroll(hitList), BorderLayout.CENTER);
            JPanel bottom = panel(new BorderLayout(), null);
            Btn inst = new Btn("Install selected", 0); inst.addActionListener(e -> installHit());
            bottom.add(browseNote, BorderLayout.CENTER);
            bottom.add(inst, BorderLayout.EAST);
            p.add(bottom, BorderLayout.SOUTH);
            return p;
        }

        JComponent settingsCard() {
            JPanel p = panel(new GridBagLayout(), null);
            GridBagConstraints c = new GridBagConstraints();
            c.anchor = GridBagConstraints.NORTHWEST; c.fill = GridBagConstraints.HORIZONTAL; c.gridx = 0; c.weightx = 1;
            int y = 0;
            JComponent[][] rows = {
                    {label("Memory (MB)", Font.PLAIN, 12, MUTED), ram},
                    {label("Java path", Font.PLAIN, 12, MUTED), javaPath},
                    {label("Extra JVM flags", Font.PLAIN, 12, MUTED), jvmArgs},
            };
            for (JComponent[] r : rows) {
                c.gridy = y++; c.insets = new Insets(0, 0, 6, 0); p.add(r[0], c);
                c.gridy = y++; c.insets = new Insets(0, 0, 16, 0); p.add(r[1], c);
            }
            c.gridy = y++; c.insets = new Insets(0, 0, 20, 0); p.add(optFlags, c);
            JPanel row = panel(new FlowLayout(FlowLayout.LEFT, 0, 0), null);
            Btn save = new Btn("Save settings", 0); save.addActionListener(e -> saveInst());
            Btn del = new Btn("Delete instance", 3); del.addActionListener(e -> deleteInst());
            row.add(save); row.add(Box.createHorizontalStrut(10)); row.add(del);
            c.gridy = y++; c.weighty = 0; p.add(row, c);
            c.gridy = y; c.weighty = 1; p.add(panel(new BorderLayout(), null), c);
            JPanel wrap = panel(new BorderLayout(), null);
            wrap.add(p, BorderLayout.NORTH);
            p.setPreferredSize(new Dimension(520, 400));
            JPanel left = panel(new FlowLayout(FlowLayout.LEFT, 0, 0), null);
            left.add(p);
            return left;
        }

        static String count(long n) {
            if (n >= 1_000_000) return String.format("%.1fM", n / 1e6);
            if (n >= 1_000) return String.format("%.1fk", n / 1e3);
            return String.valueOf(n);
        }

        void reload(String selectId) {
            loading = true;
            instModel.clear();
            for (Inst i : loadInstances()) instModel.addElement(i);
            String want = selectId != null ? selectId : S(settings().get("last"));
            int pick = instModel.isEmpty() ? -1 : 0;
            for (int i = 0; i < instModel.size(); i++) if (instModel.get(i).id.equals(want)) pick = i;
            if (pick >= 0) instList.setSelectedIndex(pick); else instList.clearSelection();
            loading = false;
            show(cur());
        }

        void show(Inst i) {
            if (i == null) {
                title.setText("Skeleton"); sub.setText("Create an instance to begin");
                cards.show(content, "empty");
                return;
            }
            title.setText(i.name); sub.setText(i.sub());
            ram.setText(String.valueOf(i.ram)); javaPath.setText(i.java); jvmArgs.setText(i.jvm); optFlags.setSelected(i.opt);
            browseNote.setText("Mods install into " + i.name + ". Modpacks create a new instance.");
            refreshMods();
            String sel = tabsGroup.getSelection() == null ? "play" : tabsGroup.getSelection().getActionCommand();
            cards.show(content, sel);
        }

        void refreshMods() {
            Inst i = cur();
            modModel.clear();
            if (i == null) return;
            Path d = i.game().resolve("mods");
            if (Files.isDirectory(d)) {
                try (Stream<Path> s = Files.list(d)) {
                    s.filter(p -> p.toString().endsWith(".jar") || p.toString().endsWith(".jar.disabled"))
                            .sorted(Comparator.comparing(p -> p.getFileName().toString().toLowerCase()))
                            .forEach(modModel::addElement);
                } catch (IOException ignored) { }
            }
            modCount.setText(modModel.size() + (modModel.size() == 1 ? " mod" : " mods"));
        }

        void toggleMod() {
            Path p = modList.getSelectedValue();
            if (p == null) return;
            String n = p.getFileName().toString();
            try {
                Files.move(p, p.resolveSibling(n.endsWith(".disabled") ? n.substring(0, n.length() - 9) : n + ".disabled"));
            } catch (IOException e) { message("Could not rename", msg(e)); }
            refreshMods();
        }
        void removeMod() {
            Path p = modList.getSelectedValue();
            if (p == null) return;
            if (ask("Remove mod", "Delete " + p.getFileName() + " from this instance?", "Remove", true)) {
                try { Files.delete(p); } catch (IOException e) { message("Could not delete", msg(e)); }
                refreshMods();
            }
        }
        void openFolder() {
            Inst i = cur();
            if (i == null) return;
            try {
                Files.createDirectories(i.game());
                Desktop.getDesktop().open(i.game().toFile());
            } catch (Exception e) { message("Could not open folder", i.game().toString()); }
        }

        void saveInst() {
            Inst i = cur();
            if (i == null) return;
            try {
                int r = Integer.parseInt(ram.getText().trim());
                if (r < 512) throw new NumberFormatException();
                i.ram = r;
            } catch (NumberFormatException e) { message("Check memory", "Enter memory in MB, at least 512."); return; }
            i.java = javaPath.getText().trim(); i.jvm = jvmArgs.getText().trim(); i.opt = optFlags.isSelected();
            try { i.save(); logLine("Saved settings for " + i.name); } catch (IOException e) { message("Could not save", msg(e)); }
        }
        void deleteInst() {
            Inst i = cur();
            if (i == null) return;
            if (ask("Delete instance", "Delete " + i.name + " with all its worlds, mods and settings? This cannot be undone.", "Delete", true)) {
                task(() -> { deleteTree(i.dir()); ui(() -> reload(null)); logLine("Deleted " + i.name); });
            }
        }

        void play() {
            Inst in = cur();
            if (in == null) return;
            if (proc != null && proc.isAlive()) { proc.destroy(); return; }
            saveSettings();
            String name = cleanName(user.getText());
            task(() -> {
                ui(() -> cards.show(content, "play"));
                logLine("Preparing " + in.name + " as " + name);
                List<String> cmd = prepare(in, name, this::logLine, this::progress);
                Files.createDirectories(in.game());
                Process p = new ProcessBuilder(cmd).directory(in.game().toFile()).redirectErrorStream(true).start();
                proc = p;
                ui(() -> playBtn.setText("Stop"));
                logLine("Launched.");
                Thread t = new Thread(() -> {
                    try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                        String l;
                        while ((l = r.readLine()) != null) logLine(l);
                    } catch (IOException ignored) { }
                    int code = -1;
                    try { code = p.waitFor(); } catch (InterruptedException ignored) { }
                    logLine("Game exited with code " + code);
                    ui(() -> playBtn.setText("Play"));
                }, "skeleton-game");
                t.setDaemon(true);
                t.start();
            });
        }

        void newInstanceDialog() {
            JDialog d = new JDialog(frame, "New instance", true);
            JPanel p = panel(new GridBagLayout(), BG);
            p.setBorder(new EmptyBorder(20, 22, 18, 22));
            JTextField name = field("My instance");
            JComboBox<String> ver = combo(), loader = combo();
            ver.setPreferredSize(new Dimension(300, 36)); loader.setPreferredSize(new Dimension(300, 36));
            name.setPreferredSize(new Dimension(300, 36));
            loader.addItem("Vanilla"); loader.addItem("Fabric"); loader.addItem("Quilt");
            Chk snaps = new Chk("Show snapshots", false);
            List<String[]> versions = new ArrayList<>();
            Runnable fill = () -> {
                ver.removeAllItems();
                for (String[] v : versions) if (v[1].equals("release") || snaps.isSelected()) ver.addItem(v[0]);
            };
            snaps.addActionListener(e -> fill.run());
            ver.addItem("Loading versions…");
            Thread t = new Thread(() -> {
                try {
                    for (Object o : L(manifest().get("versions"))) versions.add(new String[]{S(at(o, "id")), S(at(o, "type"))});
                    ui(fill);
                } catch (IOException e) {
                    ui(() -> { ver.removeAllItems(); ver.addItem("Could not reach Mojang"); });
                }
            });
            t.setDaemon(true); t.start();
            GridBagConstraints c = new GridBagConstraints();
            c.gridx = 0; c.fill = GridBagConstraints.HORIZONTAL; c.weightx = 1; c.anchor = GridBagConstraints.WEST;
            int y = 0;
            Object[][] rows = {{"Name", name}, {"Minecraft version", ver}, {"Loader", loader}};
            for (Object[] r : rows) {
                c.gridy = y++; c.insets = new Insets(0, 0, 6, 0); p.add(label((String) r[0], Font.PLAIN, 12, MUTED), c);
                c.gridy = y++; c.insets = new Insets(0, 0, 14, 0); p.add((Component) r[1], c);
            }
            c.gridy = y++; c.insets = new Insets(0, 0, 18, 0); p.add(snaps, c);
            JPanel b = panel(new FlowLayout(FlowLayout.RIGHT, 8, 0), null);
            Btn cancel = new Btn("Cancel", 1); cancel.addActionListener(e -> d.dispose());
            Btn create = new Btn("Create", 0);
            create.addActionListener(e -> {
                String mc = (String) ver.getSelectedItem();
                if (mc == null || versions.stream().noneMatch(v -> v[0].equals(mc))) { message("Pick a version", "Choose a Minecraft version first."); return; }
                Inst in = new Inst();
                in.name = name.getText().trim().isEmpty() ? "Minecraft " + mc : name.getText().trim();
                in.mc = mc; in.loader = ((String) loader.getSelectedItem()).toLowerCase(); in.id = newId(in.name);
                d.dispose();
                task(() -> {
                    if (!in.loader.equals("vanilla")) in.loaderVer = latestLoader(in.loader, in.mc);
                    in.save();
                    logLine("Created " + in.name + " (" + in.sub() + ")");
                    ui(() -> reload(in.id));
                });
            });
            b.add(cancel); b.add(create);
            c.gridy = y; c.insets = new Insets(0, 0, 0, 0); p.add(b, c);
            d.setContentPane(p); d.pack(); d.setLocationRelativeTo(frame); d.setVisible(true);
        }

        void importFile() {
            JFileChooser fc = new JFileChooser();
            fc.setDialogTitle("Import a Modrinth modpack");
            fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Modrinth pack (*.mrpack)", "mrpack"));
            if (fc.showOpenDialog(frame) != JFileChooser.APPROVE_OPTION) return;
            Path f = fc.getSelectedFile().toPath();
            task(() -> { Inst in = installPack(f, this::progress, this::logLine); logLine("Imported " + in.name); ui(() -> reload(in.id)); });
        }

        void doSearch() {
            boolean packs = qType.getSelectedIndex() == 1;
            Inst in = qMatch.isSelected() && !packs ? cur() : null;
            String text = q.getText().trim();
            status.setText("Searching…");
            Thread t = new Thread(() -> {
                try {
                    List<Object> hits = search(text, packs ? "modpack" : "mod", in);
                    ui(() -> { hitModel.clear(); for (Object h : hits) hitModel.addElement(h); status.setText(hits.size() + " results"); });
                } catch (IOException e) {
                    ui(() -> { status.setText("Search failed"); message("Search failed", msg(e)); });
                }
            }, "skeleton-search");
            t.setDaemon(true); t.start();
        }

        void installHit() {
            Object h = hitList.getSelectedValue();
            if (h == null) { message("Nothing selected", "Pick a result to install."); return; }
            String id = S(at(h, "project_id")), type = S(at(h, "project_type")), nm = S(at(h, "title"));
            if ("modpack".equals(type)) {
                task(() -> {
                    Path f = downloadPack(id, this::logLine);
                    Inst in = installPack(f, this::progress, this::logLine);
                    logLine("Installed modpack " + in.name);
                    ui(() -> reload(in.id));
                });
            } else {
                Inst in = cur();
                if (in == null) { message("No instance", "Create or select an instance first."); return; }
                task(() -> {
                    installMod(id, in, new HashSet<>(), this::logLine);
                    logLine("Installed " + nm);
                });
            }
        }

        void addPerf() {
            Inst in = cur();
            if (in == null) return;
            if (in.loader.equals("vanilla")) {
                message("Needs a mod loader", "Performance mods need Fabric or Quilt. Create a new instance with one of them.");
                return;
            }
            task(() -> {
                Set<String> seen = new HashSet<>();
                int ok = 0;
                for (String slug : PERF) {
                    try { installMod(slug, in, seen, this::logLine); ok++; }
                    catch (Exception e) { logLine("Skipped " + slug + ": " + msg(e)); }
                }
                logLine("Added " + ok + " of " + PERF.length + " performance mods");
            });
        }
    }

    static void layoutTree(Component c) {
        if (c instanceof Container) {
            ((Container) c).doLayout();
            for (Component k : ((Container) c).getComponents()) layoutTree(k);
        }
    }

    static void selfTest() throws Exception {
        Object o = Json.parse("{\"a\":[1,2.5,true,null,\"x\\n\\u0041\"],\"b\":{\"c\":-3}}");
        if (!Json.write(o).equals("{\"a\":[1,2.5,true,null,\"x\\nA\"],\"b\":{\"c\":-3}}")) throw new AssertionError(Json.write(o));
        if (!mavenPath("net.fabricmc:fabric-loader:0.15.11").equals("net/fabricmc/fabric-loader/0.15.11/fabric-loader-0.15.11.jar")) throw new AssertionError();
        Map<String, String> v = new HashMap<>(); v.put("x", "1$2");
        if (!expand("a ${x} ${y}", v).equals("a 1$2 ${y}")) throw new AssertionError();
        if (rulesOk(Json.parse("[{\"action\":\"allow\"},{\"action\":\"disallow\",\"os\":{\"name\":\"" + osName() + "\"}}]"))) throw new AssertionError("rules");
        if (!rulesOk(Json.parse("[{\"action\":\"allow\",\"os\":{\"name\":\"" + osName() + "\"}}]"))) throw new AssertionError("rules2");
        if (rulesOk(Json.parse("[{\"action\":\"allow\",\"features\":{\"is_demo_user\":true}}]"))) throw new AssertionError("features");
        Map<String, Object> p = M(Json.parse("{\"mainClass\":\"A\",\"libraries\":[{\"name\":\"p:p:1\"}],\"arguments\":{\"jvm\":[\"-a\"],\"game\":[\"g\"]}}"));
        Map<String, Object> c = M(Json.parse("{\"mainClass\":\"B\",\"libraries\":[{\"name\":\"c:c:1\"}],\"arguments\":{\"jvm\":[\"-b\"]}}"));
        Map<String, Object> m = merge(p, c);
        if (!"B".equals(m.get("mainClass")) || L(m.get("libraries")).size() != 2 || L(at(m, "arguments", "jvm")).size() != 2) throw new AssertionError("merge");
        if (!cleanName("a b!c").equals("abc") || !cleanName("").equals("Player")) throw new AssertionError("name");
        System.out.println("selftest ok");
    }

    public static void main(String[] a) throws Exception {
        if (a.length > 0 && a[0].equals("--selftest")) { selfTest(); return; }
        if (a.length > 1 && a[0].equals("--screenshot")) {
            System.setProperty("java.awt.headless", "true");
            String tab = a.length > 2 ? a[2] : "play";
            SwingUtilities.invokeAndWait(() -> {
                try {
                    App app = new App();
                    app.root.setSize(1060, 700);
                    if (tab.equals("browse")) {
                        String[][] fake = {{"Sodium", "jellysquid3", "52000000", "A modern rendering engine for Minecraft which greatly improves performance"},
                                {"Lithium", "jellysquid3", "38100000", "A Fabric mod which optimizes many areas of the game"},
                                {"Fabric API", "modmuss50", "99000", "Lightweight and modular API providing common hooks and intercompatibility"}};
                        for (String[] f : fake) {
                            Map<String, Object> h = new LinkedHashMap<>();
                            h.put("title", f[0]); h.put("author", f[1]); h.put("downloads", Long.parseLong(f[2])); h.put("description", f[3]);
                            app.hitModel.addElement(h);
                        }
                        app.hitList.setSelectedIndex(0);
                    }
                    if (tab.equals("play")) {
                        app.log.setText("Preparing Fabulous 1.20.1 as Steve\nUsing Fabric 0.15.11\nChecking 3106 files\nLaunched.\n[12:01:44] [main/INFO]: Loading Minecraft 1.20.1 with Fabric Loader 0.15.11\n");
                        app.bar.set(0.62); app.status.setText("Downloading 1930 of 3106");
                    }
                    app.cards.show(app.content, tab);
                    for (Enumeration<AbstractButton> en = app.tabsGroup.getElements(); en.hasMoreElements(); ) {
                        AbstractButton b = en.nextElement();
                        b.setSelected(b.getActionCommand().equals(tab));
                    }
                    layoutTree(app.root); layoutTree(app.root);
                    BufferedImage img = new BufferedImage(1060, 700, BufferedImage.TYPE_INT_RGB);
                    Graphics2D g = img.createGraphics();
                    app.root.print(g);
                    g.dispose();
                    ImageIO.write(img, "png", new File(a[1]));
                } catch (Exception e) { e.printStackTrace(); }
            });
            return;
        }
        SwingUtilities.invokeLater(() -> {
            App app = new App();
            JFrame f = new JFrame("Skeleton Launcher");
            app.frame = f;
            f.setIconImage(icon());
            f.setContentPane(app.root);
            f.setSize(1060, 700);
            f.setMinimumSize(new Dimension(900, 600));
            f.setLocationRelativeTo(null);
            f.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
            f.setVisible(true);
        });
    }
}
