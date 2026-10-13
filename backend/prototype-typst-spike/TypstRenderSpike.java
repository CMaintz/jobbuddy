// PROTOTYPE - throwaway. Run: java TypstRenderSpike.java <template.typ> <data.json> <pdf|svg>
// Shows how a backend adapter would call the typst CLI: an isolated temp root per render,
// bundled fonts only, PDF bytes on stdout, compiler diagnostics on stderr.

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class TypstRenderSpike {
  private static final Path TYPST = Path.of("bin", "typst.exe").toAbsolutePath();
  private static final Path FONTS = Path.of("fonts").toAbsolutePath();

  public static void main(String[] args) throws Exception {
    byte[] template = Files.readAllBytes(Path.of(args[0]));
    byte[] data = Files.readAllBytes(Path.of(args[1]));
    String format = args.length > 2 ? args[2] : "pdf";

    for (int i = 0; i < 5; i++) {
      long t0 = System.nanoTime();
      Result r = render(template, data, format);
      long ms = (System.nanoTime() - t0) / 1_000_000;
      System.out.printf("run %d: exit=%d bytes=%d %d ms%n", i, r.exit, r.output.length, ms);
      if (r.exit != 0) { System.out.println("stderr:\n" + r.stderr); break; }
      if (i == 0) Files.write(Path.of("out", "from-java." + format), r.output);
    }
  }

  record Result(int exit, byte[] output, String stderr) {}

  static Result render(byte[] template, byte[] data, String format) throws Exception {
    Path root = Files.createTempDirectory("typst-render-");
    try {
      Files.write(root.resolve("main.typ"), template);
      Files.write(root.resolve("data.json"), data);
      List<String> cmd = List.of(TYPST.toString(), "compile",
          "--root", root.toString(),
          "--ignore-system-fonts", "--font-path", FONTS.toString(),
          // Empty package dirs: templates may not pull @preview packages from the network.
          "--package-path", root.resolve("no-packages").toString(),
          "--package-cache-path", root.resolve("no-packages").toString(),
          "--input", "data=data.json",
          "--format", format,
          root.resolve("main.typ").toString(), "-");
      Process p = new ProcessBuilder(cmd).start();
      CompletableFuture<byte[]> out = CompletableFuture.supplyAsync(() -> readAll(p.getInputStream()));
      CompletableFuture<byte[]> err = CompletableFuture.supplyAsync(() -> readAll(p.getErrorStream()));
      if (!p.waitFor(10, TimeUnit.SECONDS)) { p.destroyForcibly(); return new Result(-1, new byte[0], "timeout"); }
      return new Result(p.exitValue(), out.get(), new String(err.get()));
    } finally {
      try (var files = Files.walk(root)) { // foundry-allow-var: prototype
        files.sorted(java.util.Comparator.reverseOrder()).forEach(f -> f.toFile().delete());
      }
    }
  }

  static byte[] readAll(InputStream in) {
    try (in; ByteArrayOutputStream buf = new ByteArrayOutputStream()) { in.transferTo(buf); return buf.toByteArray(); }
    catch (Exception e) { throw new RuntimeException(e); }
  }
}
