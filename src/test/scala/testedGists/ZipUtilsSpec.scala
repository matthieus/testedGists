package testedGists

import org.specs2._
import testedGists.ZipUtils._
import java.io.StringReader
import java.io.IOException
import org.specs2.mock._

class ZipUtilsSpec extends Specification {
  def is =

  "safeClose function should" ^
    "execute cleanup function when the main function executes normally" ! e1^
    "pass the argument to the main function and return whatever the main function returns" ! e2^
    "an exception thrown from the main function is not caught" ! e3^
    "execute the cleanup function when the main function throw an exception" ! e4^
    "execute normally the main function but exception thrown in the cleanup is not caught" ! e5^
    "not cover the original IOException when the closing fails" ! e6^
    "not cover any RuntimeException when the closing fails" ! e7^
  end

  def e1 = {
    var called = false
    safeUse("some arg")(_ => called = true){ arg =>
      arg + " - processed"
    }
    called must_== true
  }

  def e2 = safeUse("my arg")(_ => Unit){
    _ must_== "my arg"
  }

  def e3 = safeUse("my arg")(_ => Unit){ _ =>
    throw new RuntimeException("someException")
    true
  } must throwA[RuntimeException](message = "someException")

  def e4 = {
    var called = false
    try {
      safeUse("some arg")(_ => called = true){ _ =>
        throw new RuntimeException("someException")
        true
      }
    } catch {case _ =>}
    called must_== true
  }

  def e5 = safeUse("my arg")(_ => throw new RuntimeException("someException")) { arg =>
    arg + " - processed"
  } must throwA[RuntimeException](message = "someException")

  def e6 = safeUse("some resource"){_ => throw new IOException("from cleanup")}{ _ =>
    throw new IOException("from main")
    true
  } must throwA[IOException](message = "from main")

  def e7 = safeUse("some resource"){_ => throw new IOException("from cleanup")}{ _ =>
    throw new RuntimeException("from main")
    true
  } must throwA[RuntimeException](message = "from main")
}