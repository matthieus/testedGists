package testedGists

import org.specs2._
import java.io._
import java.util.zip.ZipOutputStream
import testedGists.TransformFileInZip.StreamTransformer
import testedGists.TransformFileInZip.ZipGuard
import java.util.Scanner
import java.util.zip.ZipInputStream
import java.util.zip.ZipFile
import java.util.zip.ZipEntry

class TransformFileInZipSpec extends Specification { def is =

	"TransformFileInZipSpec should" ^
		"remove 'TO_DELETE' from file 'TO_BE_FOUND.txt' in root directory of zip file 'myZip.zip'" ! new zipFixture().e1

	case class zipFixture() {
		import java.util.zip._
		val tempFileFixture = File.createTempFile("fixture", "")
		val tempFileResult = File.createTempFile("result", "")
		val zipFixture = {
			safeUse(new ZipOutputStream(new FileOutputStream(tempFileFixture)))(_.close) { zos =>
				zos.putNextEntry(new ZipEntry("TO_BE_FOUND.txt"))
				printToStream(zos){_.print("blob TO_DELETE blob")}
			}
			new ZipFile(tempFileFixture)
		}

		def e1 = {
		    TransformFileInZip.transformFile(zipFixture, tempFileResult.getAbsolutePath(), "TO_BE_FOUND.txt",
		        new StreamTransformer(){
		          override def apply(input: InputStream, output: OutputStream) {
		          	transformLines(input, output) { line =>
		          		line.replaceAll("TO_DELETE", "")
		          	}
		          }
		    })
		    contentAsString(new ZipFile(tempFileResult), "TO_BE_FOUND.txt") must_== "blob  blob"
		}
	}

	def transformLines(input: InputStream, os: OutputStream)(op: String => String) {
		val scanner = new Scanner(new ZipGuard(input))
		safeUse(os)(_ match {case outZip: ZipOutputStream => outZip.closeEntry()}){os =>
	  		safeUse(scanner)(_.close) {s=>
	  			while(s.hasNextLine) {
	  				os.write(op(s.nextLine).getBytes)
	  			}
	  		}
		}
	}

	def contentAsString(zipFile: ZipFile, targetFileZipPath: String): String = {
		val zis = new ZipInputStream(new FileInputStream(zipFile.getName()))
		def findFileInZip(zis: ZipInputStream, targetFileZipPath: String) {
			val zipEntry = zis.getNextEntry
			if (zipEntry == null)
				throw new FileNotFoundException
			// TODO: handle escape of ':'
			val colonIndex = targetFileZipPath.indexOf(':')
			val currentFileName = if (colonIndex == -1) targetFileZipPath else targetFileZipPath.take(colonIndex)
			if (zipEntry.getName != currentFileName) {
				findFileInZip(zis, targetFileZipPath)
			} else {
				if (colonIndex != -1)
					findFileInZip(zis, targetFileZipPath.drop(colonIndex+2))
				// else we found it as the currentFileName
			}
		}
		findFileInZip(zis, targetFileZipPath)
		val scanner = new Scanner(zis).useDelimiter("\\A")
		if (scanner.hasNext) scanner.next else ""
	}

	def printToStream(os: OutputStream)(op: java.io.PrintWriter => Unit) {
		val p = new java.io.PrintWriter(os)
		try {op(p)}
		finally {p.flush}
	}

  def safeUse[A](resource: A)(cleanUp: A => Unit)(code: A => Unit) = {
    try {
      try { code(resource) }
      finally { cleanUp(resource) }
    } catch {
      case e: Exception => throw new RuntimeException(e)
    }
  }

// TODO:
	case class ZipBuilder(path: String) {

		val zos = new ZipOutputStream(new FileOutputStream(path))

		def startEntry(path: String)() {
			zos.putNextEntry(new ZipEntry(path))
		}
	}
}