package testedGists

import org.specs2._
import specification.Step
import java.io._
import java.util.zip.ZipOutputStream
import testedGists.TransformFileInZip.StreamTransformer
import testedGists.TransformFileInZip.ZipGuard
import java.util.Scanner
import java.util.zip._

class TransformFileInZipSpec extends Specification {
  def is =

    "TransformFileInZipSpec should"                                                          ^
      "remove 'TO_DELETE' from file 'TO_BE_FOUND.txt' in root directory of zip file"         ! context().e1 ^
      "remove 'TO_DELETE_2' from file 'TO_BE_FOUND_2.txt' in zip file contained in zip file" ! context().e2 ^
                                                                                             Step(deleteZipFixture) ^
                                                                                             end

  val zipFixture =
    safeZipBuilder(File.createTempFile("fixture", "")) {
        _
        .startEntry("TO_BE_FOUND.txt")
          .print("blob TO_DELETE blob")
        .endEntry
        .startEntry("someFile.txt")
          .print("blob?")
        .endEntry
        .startZipEntry("ZIP_TO_BE_FOUND.zip")
          .startEntry("TO_BE_FOUND_2.txt")
            .print("""|first line
                      |second TO_DELETE_2 line""".stripMargin)
          .endEntry
        .endEntry
        .buildZip
      }
  def deleteZipFixture {
    new File(zipFixture.getName).delete
  }

  case class context() extends specification.After{
    val tempFileResult = File.createTempFile("result", "")
    override def after {tempFileResult.delete}

    def e1 = this {
      TransformFileInZip.transformFile(zipFixture, tempFileResult.getAbsolutePath(), "TO_BE_FOUND.txt",
        new StreamTransformer() {
          override def apply(input: InputStream, output: OutputStream) {
            mapContent(input, output) { content =>
              content.replaceAll("TO_DELETE", "")
            }
          }
        })
      contentAsString(new ZipFile(tempFileResult), "TO_BE_FOUND.txt") must_== "blob  blob"
    }

    def e2 = this {
      TransformFileInZip.transformFile(zipFixture, tempFileResult.getAbsolutePath(), "ZIP_TO_BE_FOUND.zip/TO_BE_FOUND_2.txt",
        new StreamTransformer() {
          override def apply(input: InputStream, output: OutputStream) {
            mapContent(input, output) { content =>
              content.replaceAll("TO_DELETE_2", "")
            }
          }
        })
      val content = contentAsString(new ZipFile(tempFileResult), "ZIP_TO_BE_FOUND.zip/TO_BE_FOUND_2.txt")
      content.lines.drop(1).next must_== "second  line"
    }
  }

  def mapContent(is: InputStream, os: OutputStream)(op: String => String) {
    val writer = new PrintWriter(os)
    try {
      writer.print(op(stringFromInputStream(is)))
    } finally {
      writer.flush
    }
  }

  def contentAsString(zipFile: ZipFile, targetFileZipPath: String): String = {
    val zis = new ZipInputStream(new FileInputStream(zipFile.getName()))
    def contentOfFileInZip(zis: ZipInputStream, targetFileZipPath: String): String = {
      val zipEntry = zis.getNextEntry
      if (zipEntry == null)
        throw new FileNotFoundException("path: "+targetFileZipPath)
      if (!targetFileZipPath.startsWith(zipEntry.getName)) {
        contentOfFileInZip(zis, targetFileZipPath)
      } else {
        if (!targetFileZipPath.equals(zipEntry.getName))
          contentOfFileInZip(new ZipInputStream(zis), targetFileZipPath.drop(zipEntry.getName.length + 1))
        else {
          stringFromInputStream(zis)
        }
      }
    }
    safeClose(zis){ zis =>
      contentOfFileInZip(zis, targetFileZipPath)
    }
  }

  def stringFromInputStream(is: InputStream): String = {
    val scanner = new Scanner(is).useDelimiter("\\A")
    if (scanner.hasNext) scanner.next else ""
  }

  def printToStream(os: OutputStream)(op: java.io.PrintWriter => Unit) {
    val p = new java.io.PrintWriter(os)
    try { op(p) }
    finally { p.flush }
  }

  def safeUse[A,B](resource: A)(cleanUp: A => Unit)(code: A => B): B = {
    try {
      try { code(resource) }
      finally {
        try {
          cleanUp(resource)
          } catch {
            case e: Exception => throw new RuntimeException("Cleanup failed: " + e.printStackTrace)
          }
      }
    } catch {
      case e: Exception => throw new RuntimeException("Code application failed: " + e.printStackTrace)
    }
  }

  def safeClose[A <: Closeable, B](resource: A)(code: A => B): B = {
    safeUse(resource)(_.close)(code)
  }

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
  case class RootZipBuilder(zos: ZipOutputStream) extends ZipBuilder{
    override def endEntry() = {
      throw new UnsupportedOperationException
    }
    override def buildZip() {
      zos.finish
      zos.close
    }
  }
}