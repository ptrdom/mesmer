import sbt._

object Dependencies {

  val AirframeVersion       = "23.2.2"
  val AkkaHttpVersion       = "10.4.0"
  val AkkaManagementVersion = "1.2.0"
  val AkkaVersion           = "2.7.0"
  val ByteBuddyVersion      = "1.12.23"
  val CirceVersion          = "0.14.4"

  val GoogleAutoServiceVersion        = "1.0.1"
  val LogbackVersion                  = "1.4.5"
  val OpentelemetryVersion            = "1.22.1"
  val OpentelemetryMinor0Version      = "1.22.0"
  val OpentelemetryAlphaVersion       = "1.22.1-alpha"
  val OpentelemetryAlphaMinor0Version = "1.22.0-alpha"
  val PostgresVersion                 = "42.5.3"
  val ScalatestVersion                = "3.2.15"
  val SlickVersion                    = "3.4.1"

  val akka = Seq(
    "com.typesafe.akka" %% "akka-http"                   % AkkaHttpVersion,
    "com.typesafe.akka" %% "akka-http-spray-json"        % AkkaHttpVersion,
    "com.typesafe.akka" %% "akka-stream"                 % AkkaVersion,
    "com.typesafe.akka" %% "akka-cluster-typed"          % AkkaVersion,
    "com.typesafe.akka" %% "akka-persistence-typed"      % AkkaVersion,
    "com.typesafe.akka" %% "akka-actor-typed"            % AkkaVersion,
    "com.typesafe.akka" %% "akka-actor"                  % AkkaVersion,
    "com.typesafe.akka" %% "akka-serialization-jackson"  % AkkaVersion,
    "com.typesafe.akka" %% "akka-cluster-sharding-typed" % AkkaVersion
  )

  val akkaPersistance = Seq(
    "com.typesafe.akka"  %% "akka-persistence-typed" % AkkaVersion,
    "com.lightbend.akka" %% "akka-persistence-jdbc"  % "5.2.0",
    "com.typesafe.akka"  %% "akka-persistence-query" % AkkaVersion
  )

  val zio = Seq(
    "dev.zio" %% "zio" % "2.0.8"
  )

  val byteBuddy = Seq(
    "net.bytebuddy" % "byte-buddy"       % ByteBuddyVersion,
    "net.bytebuddy" % "byte-buddy-agent" % ByteBuddyVersion
  )

  val logback = Seq("ch.qos.logback" % "logback-classic" % LogbackVersion)

  val openTelemetryApi = Seq(
    "io.opentelemetry" % "opentelemetry-api" % OpentelemetryMinor0Version
  )

  val openTelemetryInstrumentationApiSemanticConventions = Seq(
    "io.opentelemetry.instrumentation" % "opentelemetry-instrumentation-api-semconv" % OpentelemetryAlphaVersion
  )

  val opentelemetryExtensionApi = Seq(
    "io.opentelemetry.javaagent" % "opentelemetry-javaagent-extension-api" % OpentelemetryAlphaVersion
  )

  val openTelemetryExtension = Seq(
    "com.google.auto.service"    % "auto-service"                    % GoogleAutoServiceVersion,
    "io.opentelemetry.javaagent" % "opentelemetry-javaagent-tooling" % OpentelemetryAlphaVersion
  )

  val openTelemetryMuzzle = Seq(
    "io.opentelemetry.javaagent" % "opentelemetry-muzzle"              % OpentelemetryAlphaVersion,
    "io.opentelemetry.javaagent" % "opentelemetry-javaagent-bootstrap" % OpentelemetryAlphaVersion
  )

  val openTelemetryTesting = Seq(
    "io.opentelemetry.javaagent" % "opentelemetry-testing-common" % OpentelemetryAlphaVersion
  )

  val akkaTestkit = Seq(
    "com.typesafe.akka" %% "akka-actor-testkit-typed" % AkkaVersion,
    "com.typesafe.akka" %% "akka-persistence-testkit" % AkkaVersion,
    "com.typesafe.akka" %% "akka-stream-testkit"      % AkkaVersion,
    "com.typesafe.akka" %% "akka-http-testkit"        % AkkaHttpVersion
  )

  val scalatest = Seq("org.scalatest" %% "scalatest" % ScalatestVersion)

  val akkaMultiNodeTestKit = Seq("com.typesafe.akka" %% "akka-multi-node-testkit" % AkkaVersion)
}
