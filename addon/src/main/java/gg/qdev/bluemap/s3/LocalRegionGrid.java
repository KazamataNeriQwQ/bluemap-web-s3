package gg.qdev.bluemap.s3;

import de.bluecolored.bluemap.core.storage.GridStorage;
import de.bluecolored.bluemap.core.storage.ItemStorage;
import de.bluecolored.bluemap.core.storage.compression.CompressedInputStream;
import de.bluecolored.bluemap.core.storage.compression.Compression;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Local, durable region-state grid (BlueMap 5.24+ map region-state meta data). Written to
 * {@code <root>/<gridPath>.regions.dat} as GZIP, using atomic moves. Never stored in S3.
 */
final class LocalRegionGrid implements GridStorage {
  private final Path root;
  private final S3Storage owner;

  LocalRegionGrid(Path root, S3Storage owner) {
    this.root = root;
    this.owner = owner;
  }

  private Path path(int x, int z) {
    return root.resolve(S3Storage.gridPath(x, z) + ".regions.dat");
  }

  @Override
  public ItemStorage cell(int x, int z) {
    return new GridStorageCell(this, x, z);
  }

  @Override
  public OutputStream write(int x, int z) throws IOException {
    owner.ensureOpen();
    Path target = path(x, z);
    Files.createDirectories(target.getParent());
    Path part = Files.createTempFile(target.getParent(), ".region-", ".part");
    return Compression.GZIP.compress(
        new FilterOutputStream(Files.newOutputStream(part)) {
          private boolean done;

          @Override
          public void close() throws IOException {
            if (done) return;
            done = true;
            try {
              super.close();
              owner.ensureOpen();
              Files.move(part, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException e) {
              Files.deleteIfExists(part);
              throw e;
            }
          }
        });
  }

  @Override
  public CompressedInputStream read(int x, int z) throws IOException {
    owner.ensureOpen();
    Path target = path(x, z);
    if (!Files.exists(target)) return null;
    return new CompressedInputStream(Files.newInputStream(target), Compression.GZIP);
  }

  @Override
  public void delete(int x, int z) throws IOException {
    owner.ensureOpen();
    Files.deleteIfExists(path(x, z));
  }

  @Override
  public boolean exists(int x, int z) throws IOException {
    owner.ensureOpen();
    return Files.exists(path(x, z));
  }

  @Override
  public boolean isClosed() {
    return owner.isClosed();
  }

  @Override
  public Stream<Cell> stream() throws IOException {
    owner.ensureOpen();
    if (!Files.exists(root)) return Stream.empty();
    Pattern pattern = Pattern.compile("x(-?\\d+)z(-?\\d+)\\.regions\\.dat");
    List<Cell> cells = new ArrayList<>();
    try (var paths = Files.walk(root)) {
      paths
          .filter(Files::isRegularFile)
          .forEach(
              p -> {
                String name =
                    root.relativize(p).toString().replace(File.separatorChar, '/').replace("/", "");
                Matcher m = pattern.matcher(name);
                if (m.matches())
                  cells.add(
                      new GridStorageCell(
                          this, Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2))));
              });
    }
    return cells.stream();
  }
}