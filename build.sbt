name := "testedGists"

version := "1.0"

scalaVersion := "2.9.1"

libraryDependencies ++= Seq("org.specs2" %% "specs2" % "1.12" % "test",
                         "org.specs2" %% "specs2-scalaz-core" % "6.0.1" % "test",
                         "org.mockito" % "mockito-core" % "1.9.5",
						 "com.google.guava" % "guava" % "13.0.1",
                         "ch.qos.logback" % "logback-classic" % "1.0.9"
						)
