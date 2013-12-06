testedGists
===========

testedGists because the files in this project are like Gists with tests.

Some general remarks:
- won't be maintained
- won't keep any backward compatibility
- but you can take a snapshot as of today and copy anything you like in your own code
- if you take something, consider copy/pasting some tests with it
- specs2 library is used for the tests
- the Scala code uses Exceptions by opposition to Try or Either constructs

Some code samples (see test code for complete behaviour) :

Builder to create a zip file:
```scala
safeZipBuilder(File.createTempFile("fixture", "")) {
  _
  .startEntry("someDirectory/someFile.txt")
    .print("someContent")
  .endEntry
  .startZipEntry("someEmbeddedZip.zip")
    .startEntry("someFile.txt")
      .print("someContent")
    .endEntry
  .endEntry
  .buildZip
}
```

Safely use of a resource with guaranteed execution of a function after use (ie streams, files). The cleaning function is explicitly specified:
```scala
safeUse(new FileInputStream("fileName.ext"))(res => if (res != null) res.close){ fis =>
  // do things with the fis
}
```

Safely close a resource (has to implement Closeable):
```scala
safeClose(new FileInputStream("fileName.ext")){ fis =>
  // do things with the fis
}
```

Transform the content of an InputStream containing characters to an OuputStream, the transformation being done on a string. Not efficient but handy for testing. The encoding is configurable, utf-8 by default:
```scala
transformContent(new ByteArrayInputStream("some string".getBytes), output){ s =>
    s.replaceAll("string", "content")
}
```

The big feature, patching a zip file, which can be embedded in several layers of zips, all this in a streaming fashion:
```scala
TransformFileInZip.transformFile(zipFileInput, transformedZipFilePath, zipPathToFileToTransform){
  (input: InputStream, output: OutputStream) => {
    // do something with the input to create an output
  }
}
```

and others.

ZipUtils.scala contains small utility stuff and TransformFileInZip.scala contains the zip file patching feature.
