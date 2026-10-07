/*
 * JEF Builder - Java IDE & Build Tool
 * Author: IrAutoX
 * Lead Developer: DeathAmir
 * Version: 6.0.0
 */

package ir.IrAutoX.JEFB;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class JBuild {

    public static final String NAME      = "JEF Builder";
    public static final String SHORT     = "JEFB";
    public static final String VERSION   = "6.0.0";
    public static final String AUTHOR    = "IrAutoX";
    public static final String DEVELOPER = "DeathAmir";

    public static final String SETTINGS_DIR = System.getProperty("user.home")
            + File.separator + ".jefb";
    public static final String PLUGINS_DIR = SETTINGS_DIR
            + File.separator + "plugins";
    public static final String CACHE_LIB_DIR = SETTINGS_DIR
            + File.separator + "cache" + File.separator + "libs";
    public static final String LOG_FILE = SETTINGS_DIR + File.separator + "jefb.log";

    static PrintStream LOG_STREAM = null;

    public static void main(String[] args) {
        try {
            boolean debug = false;
            List<String> rest = new ArrayList<>();
            for (String a : args) {
                if (a.equals("--debug") || a.equals("-d")) debug = true;
                else rest.add(a);
            }
            Log.init(debug);
            ensureDirs();
            setupFileLogging();

            Log.raw("=================================================");
            Log.raw("JEF Builder " + VERSION);
            Log.raw("Time  : " + LocalDateTime.now()
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            Log.raw("Java  : " + System.getProperty("java.version"));
            Log.raw("OS    : " + System.getProperty("os.name") + " "
                    + System.getProperty("os.version"));
            Log.raw("User  : " + System.getProperty("user.name"));
            Log.raw("Debug : " + debug);
            Log.raw("Args  : " + String.join(" ", args));
            Log.raw("Log   : " + LOG_FILE);
            Log.raw("=================================================");

            if (rest.isEmpty()) {
                Log.raw("No command, launching IDE...");
                Commands.ide(Collections.emptyList());
                return;
            }

            String cmd = rest.get(0);
            List<String> tail = rest.subList(1, rest.size());

            Log.raw("Command: " + cmd);

            int code = switch (cmd) {
                case "--version", "-v" -> { printVersion(); yield 0; }
                case "--help", "-h", "help" -> { printHelp(); yield 0; }
                case "ide", "open" -> Commands.ide(tail);
                case "init"   -> Commands.init(tail);
                case "build"  -> Commands.build(tail);
                case "clean"  -> Commands.clean(tail);
                case "run"    -> Commands.run(tail);
                case "jar"    -> Commands.jar(tail);
                case "info"   -> Commands.info(tail);
                case "sign"   -> Commands.sign(tail);
                case "verify" -> Commands.verify(tail);
                case "update" -> Updater.checkNow();
                case "nettest" -> netTest();
                default -> { Log.error("Unknown command: " + cmd); yield 2; }
            };
            Log.raw("Exit code: " + code);
            System.exit(code);
        } catch (Throwable t) {
            Log.error("Unexpected error: " + Log.msg(t));
            t.printStackTrace();
            System.exit(2);
        }
    }

    static void setupFileLogging() {
        try {
            File logFile = new File(LOG_FILE);
            File parent = logFile.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            LOG_STREAM = new PrintStream(new FileOutputStream(logFile, true),
                    true, "UTF-8");
            PrintStream origOut = System.out;
            PrintStream origErr = System.err;

            System.setOut(new PrintStream(new OutputStream() {
                @Override public void write(int b) throws IOException {
                    origOut.write(b);
                    LOG_STREAM.write(b);
                }
                @Override public void write(byte[] b, int off, int len)
                        throws IOException {
                    origOut.write(b, off, len);
                    LOG_STREAM.write(b, off, len);
                }
            }, true));

            System.setErr(new PrintStream(new OutputStream() {
                @Override public void write(int b) throws IOException {
                    origErr.write(b);
                    LOG_STREAM.write(b);
                }
                @Override public void write(byte[] b, int off, int len)
                        throws IOException {
                    origErr.write(b, off, len);
                    LOG_STREAM.write(b, off, len);
                }
            }, true));
        } catch (Exception e) {
            System.err.println("Cannot open log file: " + e.getMessage());
        }
    }

    static int netTest() {
        Log.raw("");
        Log.raw("=== Network Diagnostic ===");
        Log.raw("");

        String[] hosts = {
                "https://api.github.com",
                "https://github.com",
                "https://raw.githubusercontent.com",
                "https://repo1.maven.org",
                "https://google.com"
        };

        java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                .followRedirects(java.net.http.HttpClient.Redirect.NORMAL)
                .connectTimeout(java.time.Duration.ofSeconds(10))
                .build();

        for (String url : hosts) {
            long t0 = System.currentTimeMillis();
            try {
                java.net.http.HttpRequest req = java.net.http.HttpRequest
                        .newBuilder(java.net.URI.create(url))
                        .header("User-Agent", "JEFB-NetTest")
                        .timeout(java.time.Duration.ofSeconds(15))
                        .GET().build();
                java.net.http.HttpResponse<String> resp = client.send(req,
                        java.net.http.HttpResponse.BodyHandlers.ofString());
                long dt = System.currentTimeMillis() - t0;
                Log.raw("[OK]   " + url + "  ->  HTTP " + resp.statusCode()
                        + "  (" + dt + " ms, " + resp.body().length() + " bytes)");
            } catch (Exception e) {
                long dt = System.currentTimeMillis() - t0;
                Log.raw("[FAIL] " + url + "  ->  " + Log.msg(e)
                        + "  (" + dt + " ms)");
            }
        }

        Log.raw("");
        Log.raw("If all [FAIL], your JVM cannot reach the internet.");
        Log.raw("Possible reasons:");
        Log.raw("  1. Firewall / antivirus blocks java.exe");
        Log.raw("  2. Proxy required (set via -Dhttp.proxyHost=...)");
        Log.raw("  3. DNS blocked");
        Log.raw("  4. No internet connection");
        Log.raw("");
        return 0;
    }

    static void ensureDirs() {
        try {
            Files.createDirectories(Paths.get(SETTINGS_DIR));
            Files.createDirectories(Paths.get(PLUGINS_DIR));
            Files.createDirectories(Paths.get(CACHE_LIB_DIR));
        } catch (Exception e) {
            System.err.println("Cannot create settings dirs: " + e.getMessage());
        }
    }

    static void printVersion() {
        Log.raw(NAME + " " + VERSION);
        Log.raw("Author   : " + AUTHOR);
        Log.raw("Developer: " + DEVELOPER);
    }

    static void printHelp() {
        Log.raw(NAME + " " + VERSION + "  -  by " + AUTHOR + " / " + DEVELOPER);
        Log.raw("");
        Log.raw("USAGE    jefb <command> [args]");
        Log.raw("");
        Log.raw("COMMANDS");
        Log.raw("  ide, open     Launch the IDE");
        Log.raw("  init [dir]    Create a new project");
        Log.raw("  build         Compile the project (incremental)");
        Log.raw("  run           Compile and run main class");
        Log.raw("  jar           Package into a fat jar");
        Log.raw("  clean         Remove build artifacts");
        Log.raw("  info          Print project info");
        Log.raw("  sign          Sign compiled classes");
        Log.raw("  verify        Verify signatures");
        Log.raw("  update        Check GitHub for updates");
        Log.raw("  nettest       Test network connectivity");
        Log.raw("");
        Log.raw("Logs are written to: " + LOG_FILE);
    }

    private JBuild() {}
}