import scala.collection.Seq

ThisBuild / scalaVersion := "2.12.10"

ThisBuild / githubRepository := "quasar-destination-azure"

homepage in ThisBuild := Some(url("https://github.com/precog/quasar-destination-azure"))

scmInfo in ThisBuild := Some(ScmInfo(
  url("https://github.com/precog/quasar-destination-azure"),
  "scm:git@github.com:precog/quasar-destination-azure.git"))

// Include to also publish a project's tests
lazy val publishTestsSettings = Seq(
  Test / packageBin / publishArtifact := true)

val ArgonautVersion = "6.2.3"
val Fs2Version = "2.1.0"
val SpecsVersion = "4.8.3"

lazy val root = project
  .in(file("."))
  .settings(noPublishSettings)
  .aggregate(core)

lazy val core = project
  .in(file("core"))
  .settings(name := "quasar-destination-azure")
  .settings(
    performMavenCentralSync := false,
    publishAsOSSProject := false,
    quasarPluginName := "azure-dest",
    quasarPluginQuasarVersion := managedVersions.value("precog-quasar"),
    quasarPluginDestinationFqcn := Some("quasar.destination.azure.AzureDestinationModule$"),
    quasarPluginDependencies ++= Seq(
      "com.precog" %% "async-blobstore-azure" % managedVersions.value("precog-async-blobstore"),
      "com.precog" %% "async-blobstore-core" % managedVersions.value("precog-async-blobstore"),
      "io.argonaut" %% "argonaut" % ArgonautVersion,
      "co.fs2" %% "fs2-core" % Fs2Version),
    libraryDependencies ++= Seq(
      "org.specs2" %% "specs2-core" % SpecsVersion % Test,
      "com.precog" %% "quasar-foundation" % managedVersions.value("precog-quasar"),
      "org.specs2" %% "specs2-scalacheck" % SpecsVersion % Test,
      "org.specs2" %% "specs2-scalaz" % SpecsVersion % Test,
      "com.precog" %% "quasar-foundation" % managedVersions.value("precog-quasar") % Test classifier "tests"))
  .enablePlugins(QuasarPlugin)
