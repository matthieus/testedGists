package testedGists;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilterInputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.io.ByteStreams;
import com.google.common.io.Closeables;

/**
 * Tool to find and transform an arbitrary file in a zip file or nested zip
 * files.
 */
public class TransformFileInZip {

  private static final Logger logger = LoggerFactory.getLogger(TransformFileInZip.class);

  /**
   * Allows defining the transformation to apply to the file being transformed
   */
  public interface StreamTransformer {
    void apply(InputStream input, OutputStream output);
  }

  /**
   * Wrapper around a ZipInputStream to avoid the xml parsing to close the
   * stream
   */
  public static class ZipInputGuard extends FilterInputStream {
    public ZipInputGuard(InputStream is) {
      super(is);
    }

    @Override
    public void close() throws IOException {
      if (in instanceof ZipInputStream)
        ((ZipInputStream) in).closeEntry();
      else
        in.close();
    }
  }

  /**
   * Wrapper around a ZipInputStream to avoid the xml parsing to close the
   * stream
   */
  public static class ZipOutputGuard extends FilterOutputStream {
    public ZipOutputGuard(OutputStream os) {
      super(os);
    }

    @Override
    public void close() throws IOException {
      if (out instanceof ZipOutputStream)
        ((ZipOutputStream) out).closeEntry();
      else
        out.close();
    }
  }

  /**
   * How fileToTransformPath works:<br>
   * <ul>
   * <li>when parsing the given path, zip file names are considered as
   * directories
   * <li>file contained in a zip inside zip,
   * "[directory/]zipFile/[directory/]file"</li>
   * <li>you can add more levels of zip containing zips,
   * "zipLevel1/zipLevel2/.../zipLeveln/file"</li>
   * <li>full filename and directory is expected, no partial name search</li>
   * </ul>
   * fileToTransformPath sample:
   * <code>uwb-war-4.7-SNAPSHOT.war/WEB-INF/web.xml</code>
   *
   * @param zipFile
   * @param targetZipFilePath
   *          the path of the new zip file to create
   * @param fileToTransformPath
   *          the path inside the zip (or multiple zips) to the file to
   *          transform, cannot be a directory or a zip file
   * @param transformer
   *          the function object which will be applied to the target file
   * @throws IOException
   */
  public static boolean transformFile(ZipFile zipFile,
      String targetZipFilePath, String fileToTransformPath,
      StreamTransformer transformer) {
    long start = System.currentTimeMillis();
    logger.debug("Processing zip file \"" + zipFile.getName() + "\".");
    ZipInputStream zipInputStream = null;
    ZipOutputStream zipOutputStream = null;
    boolean threw = false;
    try {
      zipInputStream = new ZipInputStream(
          new FileInputStream(zipFile.getName()));
      zipOutputStream = new ZipOutputStream(new FileOutputStream(
          targetZipFilePath));
      if (!transformZipStream(zipInputStream, zipOutputStream,
          fileToTransformPath, transformer)) {
        logger.error("The given file has not been found in the given zip file. No transformation applied. No file created.");
        zipOutputStream.close();
        new File(targetZipFilePath).delete();
        return false;
      }
      logger.debug("New zip file: \"" + targetZipFilePath + "\"");
      logger.info("Execution time: "
          + (System.currentTimeMillis() - start) + "ms");
      return true;
    } catch (IOException e) {
      logger.error("IO processing failed: "+e.getMessage());
      threw = true;
      throw new RuntimeException(e);
    } finally {
      try {
        Closeables.close(zipInputStream, threw);
        Closeables.close(zipOutputStream, threw);
      } catch (IOException e) {
        logger.error("IO closing failed: "+e.getMessage());
        throw new RuntimeException(e);
      }
    }
  }

  /**
   * Recursive method which digs into the relevant war or modifies the target
   * file.
   *
   * @param zipInputStream
   * @param zipOutputStream
   * @param fileToTransformPath
   *          the path to the file to transform (see transformFile() doc for
   *          details)
   * @param transformer
   * @return true if the file has been found and transformed, false otherwise
   */
  static boolean transformZipStream(ZipInputStream zipInputStream,
      ZipOutputStream zipOutputStream, String fileToTransformPath,
      StreamTransformer transformer) {
    boolean applied = false;
    boolean threw = false;
    try {
      ZipEntry zipEntry;
      while ((zipEntry = zipInputStream.getNextEntry()) != null) {
        logger.debug("Processing entry '"+zipEntry.getName()+"'");
        String zipEntryName = zipEntry.getName();
        zipOutputStream.putNextEntry(new ZipEntry(zipEntryName));
        if (fileToTransformPath.startsWith(zipEntryName)) {
          if (fileToTransformPath.equals(zipEntryName)) { // found it!
            transformer.apply(new ZipInputGuard(zipInputStream), new ZipOutputGuard(zipOutputStream));
            applied = true;
          } else { // zipEntryName is an embedded zip, we need to digging into
                   // more zip files
            applied = transformZipStream(new ZipInputStream(zipInputStream),
                new ZipOutputStream(zipOutputStream),
                fileToTransformPath.substring(zipEntryName.length() + 1),
                transformer);
          }
        } else {
          if (!zipEntry.isDirectory())
            ByteStreams.copy(zipInputStream, zipOutputStream);
        }
        zipOutputStream.closeEntry();
        zipInputStream.closeEntry();
      }
    } catch (Exception e) {
      logger.error("IO processing failed: "+e.getMessage());
      threw = true;
      throw new RuntimeException(e);
    } finally {
      try {
        zipOutputStream.finish();
        zipInputStream.closeEntry();
      } catch (IOException e) {
        logger.error("Stream closing failed: "+e.getMessage());
        if (!threw)
          throw new RuntimeException(e);
      }
    }
    return applied;
  }
}