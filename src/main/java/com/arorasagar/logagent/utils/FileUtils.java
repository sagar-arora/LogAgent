package com.arorasagar.logagent.utils;

import com.arorasagar.logagent.LogPusherConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPOutputStream;

public final class FileUtils {
  private static Logger logger = LoggerFactory.getLogger(FileUtils.class);
  private FileUtils() {}

  public static byte[] calculateMd5ofFileBytes(File file) throws IOException, NoSuchAlgorithmException {
    MessageDigest digest = MessageDigest.getInstance("MD5");
    try (FileInputStream fis = new FileInputStream(file)) {

      // Create byte array to read data in chunks
      byte[] byteArray = new byte[1024];
      int bytesCount = 0;

      // read the data from file and update that data in
      // the message digest
      while ((bytesCount = fis.read(byteArray)) != -1) {
        digest.update(byteArray, 0, bytesCount);
      }
    }

    return digest.digest();
  }

  public static String makeRelative(String string) {
    if (string.startsWith("/")) {
      return string;
    }
    return "/" + string;
  }

  public static String calculateMd5ofFile(File file)
      throws IOException, NoSuchAlgorithmException {
    byte[] md5 = calculateMd5ofFileBytes(file);
    StringBuilder result = new StringBuilder();
    for (byte b : md5) {
      result.append(Integer.toString((b & 0xff) + 0x100, 16).substring(1));
    }
    return result.toString();
  }

  public static List<File> discoverFiles(final LogPusherConfig.LogConfig logConfig) {
    final List<File> result = new ArrayList<>();
    final Path thisDir = Paths.get(logConfig.getLocalDirectory());

    try {
      Files.walkFileTree(thisDir, new SimpleFileVisitor<Path>() {

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
            throws IOException {
          if (logConfig.accepts(file.toString())) {
            File theFile = file.toFile();
            if (theFile.canRead()) {
              result.add(theFile);
            }
          }
          return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
          if (logConfig.isRecursive() || thisDir.equals(dir)) {
            return FileVisitResult.CONTINUE;
          }
          return FileVisitResult.SKIP_SUBTREE;
        }

        @Override
        public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
          logger.warn("Failed to visit {}, reason: {}", file.toAbsolutePath(), exc.toString());
          return FileVisitResult.CONTINUE;
        }
      });
    } catch (IOException e) {
      e.printStackTrace();
    }

    return result;
  }

  public static String join(String prefix, String suffix) {
    if (prefix.endsWith(suffix)) {
      return prefix;
    }
    return prefix + suffix;
  }

  public static Path getCompressedFilePathToUpload(File tmpDirectory, File inputFile) {
    return Paths.get(new File(tmpDirectory, join(inputFile.getAbsolutePath(), ".gz")).getPath());
  }

  public static File getArchivedFile(File file) {
    return new File(join(file.getAbsolutePath(), ".gz"));
  }

  public static void compressGzip(Path source, Path target) throws IOException {

    try (GZIPOutputStream gos = new GZIPOutputStream(
        new FileOutputStream(target.toFile()));
         FileInputStream fis = new FileInputStream(source.toFile())) {

      // copy file
      byte[] buffer = new byte[1024];
      int len;
      while ((len = fis.read(buffer)) > 0) {
        gos.write(buffer, 0, len);
      }
    } catch (Exception e) {
      logger.info("Exception occured {}", e.getMessage());
    }
    }
  }
