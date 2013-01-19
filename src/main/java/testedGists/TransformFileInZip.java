package testedGists;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

import com.google.common.io.ByteStreams;

/**
 * Tool to find and transform an arbitrary file in a zip file or nested zip
 * files.
 */
public class TransformFileInZip {

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
  public static class ZipGuard extends FilterInputStream {
    public ZipGuard(InputStream is) {
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
   */
  public static void transformFile(ZipFile zipFile, String targetZipFilePath,
      String fileToTransformPath, StreamTransformer transformer) {
    long start = System.currentTimeMillis();
    System.out.println("Processing zip file \"" + zipFile.getName() + "\".");
    ZipInputStream zipInputStream = null;
    ZipOutputStream zipOutputStream = null;
    try {
      zipInputStream = new ZipInputStream(
          new FileInputStream(zipFile.getName()));
      zipOutputStream = new ZipOutputStream(new FileOutputStream(
          targetZipFilePath));
      if (!transformZipStream(zipInputStream, zipOutputStream,
          fileToTransformPath, transformer)) {
        System.err
            .println("The given file has not been found in the given zip file. No transformation applied.");
      }
      System.out.println("New zip file: \"" + targetZipFilePath + "\"");
      System.out.println("Execution time: "
          + (System.currentTimeMillis() - start) + "ms");
    } catch (Exception e) {
      throw new RuntimeException(e);
    } finally {
      try {
        zipInputStream.close();
        zipOutputStream.close();
      } catch (IOException e) {
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
    try {
      ZipEntry zipEntry = zipInputStream.getNextEntry();
      while (zipEntry != null) {
        String zipEntryName = zipEntry.getName();
        zipOutputStream.putNextEntry(new ZipEntry(zipEntryName));
        if (fileToTransformPath.startsWith(zipEntryName)) {
          if (fileToTransformPath.equals(zipEntryName)) { // we are looking for
                                                          // the file itself, no
                                                          // more zips to dig in
            transformer.apply(new ZipGuard(zipInputStream), zipOutputStream);
            applied = true;
          } else { // we are digging into more zip files
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
        zipEntry = zipInputStream.getNextEntry();
      }
    } catch (Exception e) {
      throw new RuntimeException(e);
    } finally {
      try {
        zipOutputStream.finish();
        zipInputStream.closeEntry();
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
    return applied;
  }
}