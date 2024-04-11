package com.arorasagar.logagent.utils;

import com.arorasagar.logagent.LogAgentConfig;
import lombok.extern.slf4j.Slf4j;

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

@Slf4j
public final class FileUtils {
  private FileUtils() {}

  public static File archiveFile(String tmpDir, File fileToUpload) throws IOException {

    Path tmpDirectory = Paths.get(tmpDir);
    if (!Files.exists(tmpDirectory) && Files.isReadable(tmpDirectory)) {
      Files.createFile(tmpDirectory);
    }
    Path compressedFileToUploadPath = FileUtils.getCompressedFilePathToUpload(new File(tmpDir), fileToUpload);
    if (!Files.exists(compressedFileToUploadPath)) {
      Path parent = compressedFileToUploadPath.getParent();
      if (!Files.exists(parent)) {
        Files.createDirectories(parent);
      }
      Files.createFile(compressedFileToUploadPath);
    }
    log.info("Path found : {}", compressedFileToUploadPath.toString());
    FileUtils.compressGzip(fileToUpload.toPath(), compressedFileToUploadPath);
    return compressedFileToUploadPath.toFile();
  }

  public static byte[] calculateMd5ofFileBytes(File file) throws IOException {
    MessageDigest digest = null;
    try {
      digest = MessageDigest.getInstance("MD5");
    } catch (NoSuchAlgorithmException e) {
      // should never reach here as java has instance of MD5.
    }
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
      throws IOException {

    byte[] md5 = calculateMd5ofFileBytes(file);
    StringBuilder result = new StringBuilder();

    for (byte b : md5) {
      result.append(Integer.toString((b & 0xff) + 0x100, 16).substring(1));
    }

    return result.toString();
  }

  public static List<File> discoverFiles(final LogAgentConfig.LogFileConfig logFileConfig) {
    final List<File> result = new ArrayList<>();
    final Path thisDir = Paths.get(logFileConfig.getLocalDirectory());

    try {
      Files.walkFileTree(thisDir, new SimpleFileVisitor<Path>() {

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
            throws IOException {
          if (logFileConfig.accepts(file.toString())) {
            File theFile = file.toFile();
            if (theFile.canRead()) {
              result.add(theFile);
            }
          }
          return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
          if (logFileConfig.isRecursive() || thisDir.equals(dir)) {
            return FileVisitResult.CONTINUE;
          }
          return FileVisitResult.SKIP_SUBTREE;
        }

        @Override
        public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
          log.warn("Failed to visit {}, reason: {}", file.toAbsolutePath(), exc.toString());
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

    String inputFileGzip = join(inputFile.getName(), ".gz");
    return Paths.get(new File(tmpDirectory, inputFileGzip).getPath());
    //return Paths.get(new File(tmpDirectory, join(inputFile.getAbsolutePath(), ".gz")).getPath());
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
      log.info("Exception occurred {}", e.getMessage());
    }
    }
  }
