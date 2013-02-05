package testedGists

import java.util.Scanner
import java.util.zip._
import java.io._
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/** Bunch of methods which can be useful when handling zip streams. */
object ZipUtils {

  private val logger = LoggerFactory.getLogger(this.getClass())

  /** @param is
    * @param os
    * @param op
    */
  def transformContent(is: InputStream, os: OutputStream, charset: String = "utf-8")(f: String => String) {
    val pw = new PrintWriter(new BufferedWriter(new OutputStreamWriter(os, charset)))
    try { pw.write(f(stringFromInputStream(is, charset))) }
    finally { pw.flush }
  }

  /** Return a string version of an InputStream.
    *
    * @param is
    * @param charset
    * @return the string representation of the given InputStream
    */
  def stringFromInputStream(is: InputStream, charset: String = "utf-8"): String = {
    val scanner = new Scanner(is, charset).useDelimiter("\\A")
    if (scanner.hasNext) scanner.next else ""
  }

  def transformStreamedContent(is: InputStream, os: OutputStream, charset: String = "utf-8")(f: (Scanner, PrintWriter) => Unit) {
    val pw = new PrintWriter(new BufferedWriter(new OutputStreamWriter(os, charset)))
    try { f(new Scanner(is, charset), pw) }
    finally { pw.flush }
  }

  /** Returns the content of a target file as a string. Uses the Zip path where embedded zip files are considered as directories.
    * Keep in mind that the file you are targeting could be large, in that case using a streaming method to access the file would
    * be a more memory friendly method.
    *
    * @param zipFile
    * @param targetFileZipPath
    * @return the content of the target file as a String
    * @throw
    */
  def contentAsString(zipFile: ZipFile, targetFileZipPath: String, charset: String = "utf-8"): String = {
    val zis = new ZipInputStream(new FileInputStream(zipFile.getName()))
    def contentOfFileInZip(zis: ZipInputStream, targetFileZipPath: String): String = {
      val zipEntry = zis.getNextEntry
      if (zipEntry == null)
        throw new FileNotFoundException()
      if (!targetFileZipPath.startsWith(zipEntry.getName)) {
        contentOfFileInZip(zis, targetFileZipPath)
      } else {
        if (!targetFileZipPath.equals(zipEntry.getName))
          contentOfFileInZip(new ZipInputStream(zis), targetFileZipPath.drop(zipEntry.getName.length + 1))
        else {
          stringFromInputStream(zis, charset)
        }
      }
    }
    safeClose(zis) { zis =>
      try {
        contentOfFileInZip(zis, targetFileZipPath)
      } catch {
        // just to add more information
        case e: FileNotFoundException => {
          val msg = "Zip path '"+targetFileZipPath+"'' not found in file '"+zipFile.getName+"'"
          logger.error(msg)
          throw new FileNotFoundException(msg)
        }
      }
    }
  }

  /** Prints to a given OutputStream using a PrintWriter.
    *
    * @param os
    * @param op
    */
  def printToStream(os: OutputStream, charset: String = "utf-8")(f: java.io.PrintWriter => Unit) {
    val p = new java.io.PrintWriter(new BufferedWriter(new OutputStreamWriter(os, charset)))
    try { f(p) }
    finally { p.flush }
  }

  /** Safely use a resource by executing the given cleanup method in a finally.
    * Exception handling is done in a way so an exception thrown from the cleanup code won't obfuscate the main function.
    *
    * @param resource
    * @param cleanUp
    * @param code
    * @return the value returned by the code function
    */
  def safeUse[A, B](resource: A)(cleanUp: A => Unit)(f: A => B): B = {
    var threw = false
    try { f(resource) }
    catch {
      case e: Exception => {
        logger.error("IO resource processing failed: "+e.getMessage)
        threw = true
        throw e
      }
    } finally {
      try {
        cleanUp(resource)
      } catch {
        case e: Exception => {
          logger.error("IO resource cleanUp failed: "+e.getMessage)
          if (!threw) throw e
        }
      }
    }
  }

  /** Safely close the resource being used by the function code.
    *
    * @param resource
    * @param code
    * @return the value returned by the code function
    */
  def safeClose[A <: Closeable, B](resource: A)(f: A => B): B = {
    safeUse(resource)(res => if (res != null) res.close)(f)
  }

  /** Given two zip files, find the first file differing between the 2 zip files. The order of the files in the zip matters.
    *
    * @param zipFile1
    * @param zipFile2
    * @return the Zip path of the mismatching files, left string being the path in zipFile1 and right string being the path in zipFile2
    */
  def findFirstDifferentFilesAsPaths(zipFile1: ZipFile, zipFile2: ZipFile): Option[(String, String)] = {
    val zis1 = new ZipInputStream(new FileInputStream(zipFile1.getName))
    val zis2 = new ZipInputStream(new FileInputStream(zipFile2.getName))
    def findFirstDifferentFile(zis1: ZipInputStream, zis2: ZipInputStream, currentPath: String = ""): Option[(String, String)] = {
      val entry1 = zis1.getNextEntry
      val entry2 = zis2.getNextEntry
      val bis1 = new BufferedInputStream(zis1, 1024)
      val bis2 = new BufferedInputStream(zis2, 1024)
      val isEntry1Zip = isZip(bis1)
      val isEntry2Zip = isZip(bis2)
      val currentPath1 = currentPath + entry1
      val currentPath2 = currentPath + entry2
      (entry1, entry2) match {
        // no more entries to compare
        case (null, null) => {
          None
        }
        // one of the 2 zip files has at least one more file than the other one
        case (entry1, entry2) if (entry1 == null || entry2 == null) => {
          Some((currentPath1, currentPath2))
        }
        // one entry is a zip but not the other one
        case (entry1, entry2) if (isEntry1Zip != isEntry2Zip) => {
          Some((currentPath1, currentPath2))
        }
        // the entries don't have the same name
        case (entry1, entry2) if (entry1.getName != entry2.getName) => {
          Some((currentPath1, currentPath2))
        }
        // entries are zip, we need to compare what's inside that zip
        // and if nothing found, we need to continue the search
        // otherwise we return our find
        case (entry1, entry2) if (isEntry1Zip) => {
          findFirstDifferentFile(new ZipInputStream(bis1), new ZipInputStream(bis2), currentPath1+"/") match {
            case None => findFirstDifferentFile(zis1, zis2, currentPath)
            case somePathsWithDifference => somePathsWithDifference
          }
        }
        // same files, we continue the search
        case (entry1, entry2) if (sameInputStreams(bis1, bis2)) => {
          findFirstDifferentFile(zis1, zis2, currentPath)
        }
        // last possibility, the entries are different so we return
        case _ => {
          Some((currentPath1, currentPath2))
        }
      }
    }
    findFirstDifferentFile(zis1, zis2)
  }

  /** Checks if the given BufferedInputStream is zipped.
    * The BufferedInputStream will be resetted to the initial state but not the underlying Stream.
    *
    * @param bis
    * @return true if the given BufferedInputStream is a zipped stream, false otherwise
    */
  def isZip(bis: BufferedInputStream): Boolean = {
    bis.mark(128)
    val zbis = new ZipInputStream(bis)
    try {
      if (zbis.getNextEntry == null) false else true
    } finally {
      bis.reset
    }
  }

  /** @param is1
    * @param is2
    * @return true if both InputStreams are equal
    */
  def sameInputStreams(is1: InputStream, is2: InputStream): Boolean = {
    val buffer1 = new Array[Byte](1024)
    val buffer2 = new Array[Byte](1024)
    Stream.continually {
      val read1 = is1.read(buffer1, 0, buffer1.length)
      val read2 = is2.read(buffer2, 0, buffer2.length)
      read1 != -1 && read1 == read2 && buffer1.sameElements(buffer2)
    }.takeWhile(_ == true)
    buffer1.sameElements(buffer2)
  }

  /** Allows creating a ZipFile safely (the ZipOutputStream is closed in a finally) from a ZipBuilder.
    * This method should always be used when building a zip file from a ZipBuilder.
    *
    * @param file
    * @param op
    * @return the created ZipFile
    */
  def safeZipBuilder(file: File)(op: RootZipBuilder => Unit): ZipFile = {
    safeClose(new ZipOutputStream(new FileOutputStream(file))) { zos =>
      op(RootZipBuilder(zos))
    }
    new ZipFile(file)
  }
  case class EntryBuilder(parent: ZipBuilder, zos: ZipOutputStream) {
    def print(l: String): EntryBuilder = {
      printToStream(zos)(_.print(l))
      this
    }
    def write(f: java.io.PrintWriter => Unit): EntryBuilder = {
      printToStream(zos)(f)
      this
    }
    def endEntry(): ZipBuilder = {
      zos.closeEntry
      parent
    }
  }
  trait ZipBuilder {
    val zos: ZipOutputStream
    def startEntry(path: String): EntryBuilder = {
      zos.putNextEntry(new ZipEntry(path))
      EntryBuilder(this, zos)
    }
    def startZipEntry(path: String): ZipEntryBuilder = {
      zos.putNextEntry(new ZipEntry(path))
      ZipEntryBuilder(this, new ZipOutputStream(zos))
    }
    def endEntry(): ZipBuilder
    def buildZip()
  }
  case class ZipEntryBuilder(parent: ZipBuilder, zos: ZipOutputStream) extends ZipBuilder {
    override def endEntry(): ZipBuilder = {
      zos.closeEntry
      zos.finish
      parent
    }
    override def buildZip() {
      throw new UnsupportedOperationException
    }
  }
  case class RootZipBuilder(zos: ZipOutputStream) extends ZipBuilder {
    override def endEntry() = {
      throw new UnsupportedOperationException
    }
    override def buildZip() {
      zos.finish
      zos.close
    }
  }
}