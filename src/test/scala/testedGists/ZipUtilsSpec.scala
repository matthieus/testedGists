package testedGists

import org.specs2._
import testedGists.ZipUtils._
import java.io._
import org.specs2.mock._

class ZipUtilsSpec extends Specification {
  def is =

  "safeClose function should" ^
    "execute cleanup function when the main function executes normally" ! c1.e1_1^
    "pass the argument to the main function and return whatever the main function returns" ! c1.e1_2^
    "an exception thrown from the main function is not caught" ! c1.e1_3^
    "execute the cleanup function when the main function throw an exception" ! c1.e1_4^
    "execute normally the main function but exception thrown in the cleanup is not caught" ! c1.e1_5^
    "not cover the original IOException when the closing fails" ! c1.e1_6^
    "not cover any RuntimeException when the closing fails" ! c1.e1_7^
  p^
  "contentAsString function should" ^
    "return the content of a file located in the root of the provided zip file" ! c2().e2_1^
    "return the content of a file located in an embedded zip" ! c2().e2_2^
    "return the content of a file located in a directory in several layers of embedded zips" ! c2().e2_3^
    "throw an FileNotFoundException when the file is not found with the given zip path in the message" ! c2().e2_4^
  p^
  "transformContent function should" ^
    "return the transformed stream in the given ouput stream" ! c3().e3_1^
    "preserve utf-8 encoding" ! c3().e3_2^
    "utf-8 is the default encoding" ! c3().e3_3^
    "preserve ISO-8859-1 encoding" ! c3().e3_4^
  end

  object c1 extends Mockito {

    def some(f: Unit => Unit) {f()}

    // e1_X
    def e1_1 = {
      val cleanup = mock[String => Unit]
      safeUse("resource")(cleanup){ arg =>
        arg + " - processed"
      }
      there was one(cleanup).apply("resource")
    }

    def e1_2 = safeUse("resource")(_ => Unit){
      _ must_== "resource"
    }

    def e1_3 = safeUse("resource")(_ => Unit){ _ =>
      throw new RuntimeException("someException")
      true
    } must throwA[RuntimeException](message = "someException")

    def e1_4 = {
      val cleanup = mock[String => Unit]
      try {
        safeUse("resource")(cleanup){ _ =>
          throw new RuntimeException("someException")
          true
        }
      } catch {case _ =>}
      there was one(cleanup).apply("resource")
    }

    def e1_5 = safeUse("resource")(_ => throw new RuntimeException("someException")) { arg =>
      arg + " - processed"
    } must throwA[RuntimeException](message = "someException")

    def e1_6 = safeUse("resource"){_ => throw new IOException("from cleanup")}{ _ =>
      throw new IOException("from main")
      true
    } must throwA[IOException](message = "from main")

    def e1_7 = safeUse("resource"){_ => throw new IOException("from cleanup")}{ _ =>
      throw new RuntimeException("from main")
      true
    } must throwA[RuntimeException](message = "from main")
  }

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

  case class c2() extends specification.After{

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

  case class c3() {

    val output = new ByteArrayOutputStream()

    def e3_1 = {
      transformContent(new ByteArrayInputStream("first TO_BE_DELETED line".getBytes), output){ s =>
        s.replaceAll("TO_BE_DELETED ", "")
      }
      output.toString("utf-8") must_== "first line"
    }

    def e3_2 = {
      transformContent(new ByteArrayInputStream("Γαζέες καὶ μυρτιὲς δὲν θὰ βρῶ πιὰ στὸ χρυσαφὶ ξέφωτο".getBytes("utf-8")), output, "utf-8"){ s =>
        s.replaceAll("Γαζέες καὶ μυρτιὲς δὲν θὰ βρῶ πιὰ στὸ χρυσαφὶ ξέφωτο", "D'fhuascail Íosa, Úrmhac na hÓighe Beannaithe, pór Éava agus Ádhaimh")
      }
      output.toString("utf-8") must_== "D'fhuascail Íosa, Úrmhac na hÓighe Beannaithe, pór Éava agus Ádhaimh"
    }

    def e3_3 = {
      transformContent(new ByteArrayInputStream("Γαζέες καὶ μυρτιὲς δὲν θὰ βρῶ πιὰ στὸ χρυσαφὶ ξέφωτο".getBytes("utf-8")), output){ s =>
        s.replaceAll("Γαζέες καὶ μυρτιὲς δὲν θὰ βρῶ πιὰ στὸ χρυσαφὶ ξέφωτο", "D'fhuascail Íosa, Úrmhac na hÓighe Beannaithe, pór Éava agus Ádhaimh")
      }
      output.toString("utf-8") must_== "D'fhuascail Íosa, Úrmhac na hÓighe Beannaithe, pór Éava agus Ádhaimh"
    }

    def e3_4 = {
      transformContent(new ByteArrayInputStream("Unë mund të ha qelq dhe nuk më gjen gjë.".getBytes("ISO-8859-1")), output, "ISO-8859-1"){ s =>
        s.replaceAll("Unë mund të ha qelq dhe nuk më gjen gjë.", "Falsches Üben von Xylophonmusik quält jeden größeren Zwerg")
      }
      output.toString("ISO-8859-1") must_== "Falsches Üben von Xylophonmusik quält jeden größeren Zwerg"
    }
  }
}