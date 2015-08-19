logLevel := Level.Warn
// cucumber plugin which located in the local directory
resolvers += Resolver.file("Local repo", file("~/.ivy2/local"))(Resolver.ivyStylePatterns)
addSbtPlugin("templemore" % "sbt-cucumber-plugin" % "0.9.0-SNAPSHOT")