import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.stream.Stream;

public class FilesDemo {
    public static void main(String[] args) throws IOException {
        Path dir = Files.createTempDirectory("inventory-");   // somewhere under java.io.tmpdir
        Path csv = dir.resolve("stock.csv");

        Files.writeString(csv, """
                # sku,qty
                BOLT-7,10
                NUT-3,4

                CAM-9,2
                """, StandardCharsets.UTF_8);
        System.out.println("file             = " + csv.getFileName());
        System.out.println("size in bytes    = " + Files.size(csv));

        // 1. the whole file in memory
        List<String> all = Files.readAllLines(csv, StandardCharsets.UTF_8);
        System.out.println("readAllLines     = " + all.size() + " lines");

        // 2. a lazy stream - the file stays open until the try block ends
        long units;
        try (Stream<String> lines = Files.lines(csv, StandardCharsets.UTF_8)) {
            units = lines.map(String::strip)
                         .filter(l -> !l.isEmpty() && !l.startsWith("#"))
                         .mapToLong(l -> Long.parseLong(l.split(",")[1]))
                         .sum();
        }
        System.out.println("units via lines  = " + units);

        // 3. a buffered reader, one line at a time
        int skus = 0;
        try (BufferedReader in = Files.newBufferedReader(csv, StandardCharsets.UTF_8)) {
            String line;
            while ((line = in.readLine()) != null) {
                if (line.isBlank() || line.startsWith("#")) continue;
                skus++;
            }
        }
        System.out.println("skus via reader  = " + skus);

        // directories, copies, and a walk
        Path archive = Files.createDirectories(dir.resolve("archive"));
        Path copy = Files.copy(csv, archive.resolve("stock-2026-10-15.csv"), StandardCopyOption.REPLACE_EXISTING);
        System.out.println("copied to        = " + slashes(dir.relativize(copy)));
        try (Stream<Path> walk = Files.walk(dir)) {
            System.out.println("regular files    = " + walk.filter(Files::isRegularFile)
                    .map(p -> slashes(dir.relativize(p)))
                    .sorted()
                    .toList());
        }
        System.out.println("exists           = " + Files.exists(copy)
                + ", notExists(nope.csv) = " + Files.notExists(dir.resolve("nope.csv")));

        // a missing file is a specific IOException, not a null
        try {
            Files.readString(dir.resolve("nope.csv"));
        } catch (NoSuchFileException e) {
            System.out.println("NoSuchFile       = " + Path.of(e.getFile()).getFileName());
        }

        // delete children before parents; a non-empty directory refuses
        Files.delete(copy);
        Files.delete(archive);
        Files.delete(csv);
        Files.delete(dir);
        System.out.println("cleaned up       = " + Files.notExists(dir));
    }

    // Windows separates with '\'; print '/' so the lesson reads the same everywhere
    static String slashes(Path p) { return p.toString().replace(p.getFileSystem().getSeparator(), "/"); }
}