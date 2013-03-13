package testedGists

import java.io._
import java.util.zip._
import org.slf4j._
import ZipUtils._

/** Tool to find and transform an arbitrary file in a zip file or nested zip files. */
object TransformFileInZip {

  private val logger = LoggerFactory.getLogger(this.getClass())

  def copy(from: InputStream, to: OutputStream) {
    val buffer1 = new Array[Byte](1024)
    Stream.continually {
      val read1 = from.read(buffer1, 0, buffer1.length)
      to.write(buffer1, 0, read1)
      read1 != -1
    }.takeWhile(_ == true)
  }

  /** Wrapper around a ZipInputStream to avoid the xml parsing to close the stream */
  class ZipInputGuard(is: InputStream) extends FilterInputStream(is) {
    override def close() {
      in match {
        case zis: ZipInputStream => zis.closeEntry
        case _ => in.close
      }
    }
  }

  /** Wrapper around a ZipInputStream to avoid the xml parsing to close the stream */
  class ZipOutputGuard(os: OutputStream) extends FilterOutputStream(os) {
    override def close() {
      out match {
        case zos: ZipOutputStream => zos.closeEntry
        case _ => out.close
      }
    }
  }

  /** How fileToTransformPath works:<br>
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
    * @return true if the fileToTransform was transformed, false otherwise
    */
  def transformFile(zipFile: ZipFile, targetZipFilePath: String, fileToTransformPath: String)(
      transformer: (InputStream, OutputStream) => Unit): Boolean = {
    val start = System.currentTimeMillis()
    logger.debug("Processing zip file \"" + zipFile.getName() + "\".")
    safeClose(new ZipInputStream(new FileInputStream(zipFile.getName()))) { zis =>
      safeClose(new ZipOutputStream(new FileOutputStream(targetZipFilePath))) { zos =>
        if (!transformZipStream(zis, zos, fileToTransformPath, transformer)) {
          logger.error("The given file has not been found in the given zip file. No transformation applied. No file created.")
          zos.close()
          new File(targetZipFilePath).delete()
          false
        } else {
          logger.debug("New zip file: \"" + targetZipFilePath + "\"")
          logger.info("Execution time: " + (System.currentTimeMillis() - start) + "ms")
          true
        }
      }
    }
  }

  /** Recursive method which digs into the relevant war or modifies the target
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
  def transformZipStream(zipInputStream: ZipInputStream,
      zipOutputStream: ZipOutputStream, fileToTransformPath: String,
      transformer: (InputStream, OutputStream) => Unit): Boolean = {
    var applied = false
    Stream.continually(zipInputStream.getNextEntry).takeWhile(_ != null).filter(!_.isDirectory()).foreach { zipEntry =>
      logger.debug("Processing entry '"+zipEntry.getName()+"'")
      val zipEntryName = zipEntry.getName()
      zipOutputStream.putNextEntry(new ZipEntry(zipEntryName))
      if (fileToTransformPath.startsWith(zipEntryName)) {
        if (fileToTransformPath.equals(zipEntryName)) { // found it!
          transformer(new ZipInputGuard(zipInputStream), new ZipOutputGuard(zipOutputStream))
          applied = true
        } else { // zipEntryName is an embedded zip, we need to dig into
                 // more zip files
          applied = transformZipStream(new ZipInputStream(zipInputStream),
              new ZipOutputStream(zipOutputStream),
              fileToTransformPath.substring(zipEntryName.length() + 1),
              transformer)
        }
      } else {
        if (!zipEntry.isDirectory())
          copy(zipInputStream, zipOutputStream)
      }
      zipOutputStream.closeEntry()
      zipInputStream.closeEntry()
    }
    applied
  }
}