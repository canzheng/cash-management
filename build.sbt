name := "exchange-service"

version := "1.0"

scalaVersion := "2.11.6"

libraryDependencies += "com.typesafe.akka" %% "akka-actor" % "2.3.9"

libraryDependencies += "com.typesafe.akka" %% "akka-testkit" % "2.3.9" % "test"

libraryDependencies += "org.scalatest" %% "scalatest" % "2.2.4" % "test"

libraryDependencies += "io.spray" %%  "spray-can" % "1.3.3"

libraryDependencies += "io.spray" %%  "spray-routing" % "1.3.3"

libraryDependencies += "io.spray" %%  "spray-httpx" % "1.3.3"

libraryDependencies += "io.spray"  %%  "spray-testkit" % "1.3.3" % "test"

libraryDependencies += "io.spray"  %%  "spray-json" % "1.3.2"

libraryDependencies += "io.spray"  %%  "spray-client" % "1.3.3"

libraryDependencies += "info.cukes"  %%  "cucumber-scala" % "1.2.3" % "test"



