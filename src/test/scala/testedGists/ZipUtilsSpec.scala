package testedGists

import org.specs2._
import testedGists.ZipUtils._
import java.io._
import org.specs2.mock._

class ZipUtilsSpec extends Specification {
  def is =

  "safeClose function should" ^
    "execute cleanup function when the main function executes normally" ! e1_1^
    "pass the argument to the main function and return whatever the main function returns" ! e1_2^
    "an exception thrown from the main function is not caught" ! e1_3^
    "execute the cleanup function when the main function throw an exception" ! e1_4^
    "execute normally the main function but exception thrown in the cleanup is not caught" ! e1_5^
    "not cover the original IOException when the closing fails" ! e1_6^
    "not cover any RuntimeException when the closing fails" ! e1_7^
  "contentAsString function should" ^
    "return the content of a file in the root of the provided zip file" ! context().e2_1^
    "return the content of a file in an embedded zip" ! context().e2_2^
    "return the content of a file in directory in several layers of embedded zips" ! context().e2_3^
    "throw an FileNotFoundException when the file is not found with the given zip path in the message" ! context().e2_4^
  end

// e1_X

  def e1_1 = {
    var called = false
    safeUse("some arg")(_ => called = true){ arg =>
      arg + " - processed"
    }
    called must_== true
  }

  def e1_2 = safeUse("my arg")(_ => Unit){
    _ must_== "my arg"
  }

  def e1_3 = safeUse("my arg")(_ => Unit){ _ =>
    throw new RuntimeException("someException")
    true
  } must throwA[RuntimeException](message = "someException")

  def e1_4 = {
    var called = false
    try {
      safeUse("some arg")(_ => called = true){ _ =>
        throw new RuntimeException("someException")
        true
      }
    } catch {case _ =>}
    called must_== true
  }

  def e1_5 = safeUse("my arg")(_ => throw new RuntimeException("someException")) { arg =>
    arg + " - processed"
  } must throwA[RuntimeException](message = "someException")

  def e1_6 = safeUse("some resource"){_ => throw new IOException("from cleanup")}{ _ =>
    throw new IOException("from main")
    true
  } must throwA[IOException](message = "from main")

  def e1_7 = safeUse("some resource"){_ => throw new IOException("from cleanup")}{ _ =>
    throw new RuntimeException("from main")
    true
  } must throwA[RuntimeException](message = "from main")

// e2_X

  val zipFixture =
    safeZipBuilder(File.createTempFile("fixture", "")) {
      _
      .startEntry("TO_BE_FOUND.txt")
        .print("some content")
      .endEntry
      .startEntry("someFile.txt")
        .print("some content")
      .endEntry
      .startZipEntry("ZIP_TO_BE_FOUND_2_1.zip")
        .startEntry("someFile.txt")
          .print("some content")
        .endEntry
        .startEntry("TO_BE_FOUND_2.txt")
          .print("""|first line
                    |second line""".stripMargin)
        .endEntry
      .endEntry
      .startZipEntry("ZIP_TO_BE_FOUND_3_1.zip")
        .startZipEntry("somedirectory/ZIP_TO_BE_FOUND_3_2.zip")
          .startZipEntry("ZIP_TO_BE_FOUND_3_3.zip")
            .startEntry("someFile.txt")
              .print("some content")
            .endEntry
            .startEntry("somedirectory/TO_BE_FOUND_3.txt")
              .print("""|first line
                        |second line""".stripMargin)
            .endEntry
          .endEntry
        .endEntry
        .startEntry("someFile.txt")
          .print("some content")
        .endEntry
      .endEntry
      .buildZip
    }

  case class context() extends specification.After{

    val tempFileResult = File.createTempFile("result", "")
    val tempFileExpected = File.createTempFile("expected", "")

    override def after {
      tempFileResult.delete
      tempFileExpected.delete
    }

    def e2_1 = contentAsString(zipFixture, "TO_BE_FOUND.txt") must_== "some content"

    def e2_2 = contentAsString(zipFixture, "ZIP_TO_BE_FOUND_2_1.zip/TO_BE_FOUND_2.txt") must_== """|first line
                                                                                                   |second line""".stripMargin

    def e2_3 = contentAsString(zipFixture, "ZIP_TO_BE_FOUND_3_1.zip/somedirectory/ZIP_TO_BE_FOUND_3_2.zip/ZIP_TO_BE_FOUND_3_3.zip/somedirectory/TO_BE_FOUND_3.txt") must_==
                  """|first line
                     |second line""".stripMargin
    def e2_4 = contentAsString(zipFixture, "TO_BE_NOT_FOUND.txt") must throwA[FileNotFoundException](message = "TO_BE_NOT_FOUND.txt")
  }
}