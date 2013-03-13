package testedGists

import org.specs2._
import specification.Step
import java.io._
import testedGists.ZipUtils._
import java.util.Scanner
import java.util.zip._

class TransformFileInZipSpec extends Specification {
  def is =

    "TransformFileInZipSpec should"                                                          ^
      """remove 'TO_DELETE' from file 'TO_BE_FOUND.txt' in root directory of zip file and
      create new file corresponding to the path given in argument"""                         ! context().e1 ^
      "remove 'TO_DELETE_2' from file 'TO_BE_FOUND_2.txt' in zip file contained in zip file" ! context().e2 ^
      """remove 'TO_DELETE_3' from file 'TO_BE_FOUND_3.txt' in directory in zip file
      contained in zip file contained in zip file"""                                         ! context().e3 ^
      """remove 'TO_DELETE_3' from file 'TO_BE_FOUND_3.txt' as in e2 and verify that all
      the other files stay the same"""                                                       ! context().e4 ^
      "try to transform a non existing file in the zip, will not generate a new zip"         ! context().e5 ^
      "return true when transforming successfully the file in zip"                           ! context().e6 ^
      "return false when trying to transform a non existing file in the zip"                 ! context().e7 ^
      // below takes around 3 minutes to run, mainly a check to verify memory consumption
      // "remove 'TO_DELETE' in a small file among a lot of large files in zip"                 ! context().e8 ^
      """close both Streams during the transform, verify that the new zip still contains
      the files copied after the transformation. (important for XML transformation as XML
      libs tend to close streams for you)"""                                                 ! context().e9 ^
                                                                                             Step(deleteZipFixture) ^
    end

  val zipFixture =
    safeZipBuilder(File.createTempFile("fixture", "")) {
      _
      .startEntry("TO_BE_FOUND.txt")
        .print("blob TO_DELETE blob")
      .endEntry
      .startEntry("someFile.txt")
        .print("someContent")
      .endEntry
      .startZipEntry("ZIP_TO_BE_FOUND_2_1.zip")
        .startEntry("someFile.txt")
          .print("someContent")
        .endEntry
        .startEntry("TO_BE_FOUND_2.txt")
          .print("""|first line
                    |second TO_DELETE_2 line""".stripMargin)
        .endEntry
      .endEntry
      .startZipEntry("ZIP_TO_BE_FOUND_3_1.zip")
        .startZipEntry("somedirectory/ZIP_TO_BE_FOUND_3_2.zip")
          .startZipEntry("ZIP_TO_BE_FOUND_3_3.zip")
            .startEntry("someFile.txt")
              .print("someContent")
            .endEntry
            .startEntry("somedirectory/TO_BE_FOUND_3.txt")
              .print("""|first line
                        |second TO_DELETE_3 line
                        |third line""".stripMargin)
            .endEntry
          .endEntry
        .endEntry
        .startEntry("someFile.txt")
          .print("someContent")
        .endEntry
      .endEntry
      .buildZip
    }
  def deleteZipFixture {
    new File(zipFixture.getName).delete
  }

  case class context() extends specification.After{

    val tempFileResult = File.createTempFile("result", "")
    val tempFileExpected = File.createTempFile("expected", "")

    override def after {
      tempFileResult.delete
      tempFileExpected.delete
    }

    def e1 = this {
      TransformFileInZip.transformFile(zipFixture, tempFileResult.getAbsolutePath(), "TO_BE_FOUND.txt")
          {(input: InputStream, output: OutputStream) =>
            transformContent(input, output) { content =>
              content.replaceAll("TO_DELETE", "")
            }
          }
      contentAsString(new ZipFile(tempFileResult), "TO_BE_FOUND.txt") must_== "blob  blob"
    }

    def e2 = this {
      TransformFileInZip.transformFile(zipFixture, tempFileResult.getAbsolutePath(), "ZIP_TO_BE_FOUND_2_1.zip/TO_BE_FOUND_2.txt")
          {(input: InputStream, output: OutputStream) =>
            transformContent(input, output) { content =>
              content.replaceAll("TO_DELETE_2", "")
            }
          }
      val content = contentAsString(new ZipFile(tempFileResult), "ZIP_TO_BE_FOUND_2_1.zip/TO_BE_FOUND_2.txt")
      content.lines.drop(1).next must_== "second  line"
    }

    def e3 = this {
      TransformFileInZip.transformFile(zipFixture, tempFileResult.getAbsolutePath(),
        "ZIP_TO_BE_FOUND_3_1.zip/somedirectory/ZIP_TO_BE_FOUND_3_2.zip/ZIP_TO_BE_FOUND_3_3.zip/somedirectory/TO_BE_FOUND_3.txt")
          {(input: InputStream, output: OutputStream) =>
            transformContent(input, output) { content =>
              content.replaceAll("TO_DELETE_3", "")
            }
          }
      val content = contentAsString(new ZipFile(tempFileResult),
        "ZIP_TO_BE_FOUND_3_1.zip/somedirectory/ZIP_TO_BE_FOUND_3_2.zip/ZIP_TO_BE_FOUND_3_3.zip/somedirectory/TO_BE_FOUND_3.txt")
      content.lines.drop(1).next must_== "second  line"
    }

    def e4 = this {
      TransformFileInZip.transformFile(zipFixture, tempFileResult.getAbsolutePath(),
        "ZIP_TO_BE_FOUND_3_1.zip/somedirectory/ZIP_TO_BE_FOUND_3_2.zip/ZIP_TO_BE_FOUND_3_3.zip/somedirectory/TO_BE_FOUND_3.txt")
          {(input: InputStream, output: OutputStream) =>
            transformContent(input, output) { content =>
              content.replaceAll("TO_DELETE_3", "")
            }
          }
      safeZipBuilder(tempFileExpected) {
        _
        .startEntry("TO_BE_FOUND.txt")
          .print("blob TO_DELETE blob")
        .endEntry
        .startEntry("someFile.txt")
          .print("someContent")
        .endEntry
        .startZipEntry("ZIP_TO_BE_FOUND_2_1.zip")
          .startEntry("someFile.txt")
            .print("someContent")
          .endEntry
          .startEntry("TO_BE_FOUND_2.txt")
            .print("""|first line
                      |second TO_DELETE_2 line""".stripMargin)
          .endEntry
        .endEntry
        .startZipEntry("ZIP_TO_BE_FOUND_3_1.zip")
          .startZipEntry("somedirectory/ZIP_TO_BE_FOUND_3_2.zip")
            .startZipEntry("ZIP_TO_BE_FOUND_3_3.zip")
              .startEntry("someFile.txt")
                .print("someContent")
              .endEntry
              .startEntry("somedirectory/TO_BE_FOUND_3.txt")  // 'TO_DELETE_3' is expected to be removed
                .print("""|first line
                          |second  line
                          |third line""".stripMargin)
              .endEntry
            .endEntry
          .endEntry
          .startEntry("someFile.txt")
            .print("someContent")
          .endEntry
        .endEntry
        .buildZip
      }
      findFirstDifferentFilesAsPaths(new ZipFile(tempFileExpected), new ZipFile(tempFileResult)) must_== None
    }

    def e5 = {
      TransformFileInZip.transformFile(zipFixture, tempFileResult.getAbsolutePath(), "TO_BE_NOT_FOUND.txt")
          {(input: InputStream, output: OutputStream) =>
            transformContent(input, output) { content =>
              content.replaceAll("TO_DELETE", "")
            }
          }
      tempFileResult.exists must_== false
    }

    def e6 = this {
      TransformFileInZip.transformFile(zipFixture, tempFileResult.getAbsolutePath(), "TO_BE_FOUND.txt")
          {(input: InputStream, output: OutputStream) =>
            transformContent(input, output) { content =>
              content.replaceAll("TO_DELETE", "")
            }
          } must_== true
    }

    def e7 = {
      TransformFileInZip.transformFile(zipFixture, tempFileResult.getAbsolutePath(), "TO_BE_NOT_FOUND.txt")
          {(input: InputStream, output: OutputStream) =>
            transformContent(input, output) { content =>
              content.replaceAll("TO_DELETE", "")
            }
          } must_== false
    }

    def e8 = {
      val start = System.currentTimeMillis
      val largeFixture = safeZipBuilder(File.createTempFile("largeFixture", "")) {
        _
        .startEntry("TO_BE_FOUND.txt")
          .print("blob TO_DELETE blob") // same as the fixture except here where we removed 'TO_DELETE'
        .endEntry
        .startEntry("someBigFile.txt")
          .write(pw => (1 to 10000000).foreach(i => {
                                                      pw.println("And"+i+" here "+i+" is "+i+" another "+i+" line"+i)
                                                      if (i % 100000 == 0) pw.flush
                                                    }))
        .endEntry
        .startZipEntry("ZIP_TO_BE_FOUND_2_1.zip")
          .startEntry("someFile.txt")
            .print("someContent")
          .endEntry
          .startEntry("TO_BE_FOUND_2.txt")
            .print("""|first line
                      |second TO_DELETE_2 line""".stripMargin)
          .endEntry
        .endEntry
        .startZipEntry("ZIP_TO_BE_FOUND_3_1.zip")
          .startZipEntry("somedirectory/ZIP_TO_BE_FOUND_3_2.zip")
            .startZipEntry("ZIP_TO_BE_FOUND_3_3.zip")
              .startEntry("someFile.txt")
                .print("someContent")
              .endEntry
              .startEntry("somedirectory/TO_BE_FOUND_3.txt")
                .print("""|first line
                          |second  line
                          |third line
                          |""".stripMargin)
                .write(pw => (1 to 10000000).foreach(i => {
                                                            pw.println("And"+(1000000-i)+" here "+(1000000-i)+" is "+i+" another "+(100000-i)+" line"+i)
                                                            if (i % 100000 == 0) pw.flush
                                                          }))
              .endEntry
            .endEntry
          .endEntry
          .startEntry("someFile.txt")
            .print("someContent")
          .endEntry
        .endEntry
        .buildZip
      }
      println("Creation lasted: "+(System.currentTimeMillis - start))
      TransformFileInZip.transformFile(largeFixture, tempFileResult.getAbsolutePath(), "ZIP_TO_BE_FOUND_2_1.zip/TO_BE_FOUND_2.txt")
          {(input: InputStream, output: OutputStream) =>
            transformContent(input, output) { content =>
              content.replaceAll("TO_DELETE_2", "")
            }
          }
      val content = contentAsString(new ZipFile(tempFileResult), "ZIP_TO_BE_FOUND_2_1.zip/TO_BE_FOUND_2.txt")
      content.lines.drop(1).next must_== "second  line"
    }

    def e9 = {
       TransformFileInZip.transformFile(zipFixture, tempFileResult.getAbsolutePath(), "TO_BE_FOUND.txt")
          {(input: InputStream, output: OutputStream) =>
            transformContent(input, output) { content =>
              content.replaceAll("TO_DELETE", "")
            }
            input.close
            output.close
          }
      contentAsString(new ZipFile(tempFileResult), "ZIP_TO_BE_FOUND_3_1.zip/someFile.txt") must_== "someContent"
    }
  }
}