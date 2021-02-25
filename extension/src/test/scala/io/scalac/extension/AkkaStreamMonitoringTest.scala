package io.scalac.extension

import akka.actor.PoisonPill
import akka.actor.testkit.typed.scaladsl.{ ScalaTestWithActorTestKit, TestProbe }
import akka.actor.typed.receptionist.ServiceKey
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.{ ActorRef, Behavior }
import akka.{ actor => classic }
import io.scalac.core.akka.model.PushMetrics
import io.scalac.core.model.Tag.StageName
import io.scalac.core.model.{ ConnectionStats, StageInfo }
import io.scalac.extension.AkkaStreamMonitoring.StartStreamCollection
import io.scalac.extension.event.{ ActorInterpreterStats, EventBus }
import io.scalac.extension.util.TestConfig.localActorProvider
import io.scalac.extension.util.probe.BoundTestProbe.MetricRecorded
import io.scalac.extension.util.probe.{ StreamMonitorTestProbe, StreamOperatorMonitorTestProbe }
import io.scalac.extension.util.{ MonitorFixture, TerminationRegistryOps, TestOps }
import org.scalatest._
import org.scalatest.concurrent.Eventually
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration._

class AkkaStreamMonitoringTest
    extends ScalaTestWithActorTestKit(localActorProvider)
    with AnyFlatSpecLike
    with Matchers
    with Inspectors
    with Eventually
    with OptionValues
    with Inside
    with BeforeAndAfterAll
    with TerminationRegistryOps
    with LoneElement
    with TestOps
    with MonitorFixture {

  override type Monitor = (StreamOperatorMonitorTestProbe, StreamMonitorTestProbe)
  override type Command = AkkaStreamMonitoring.Command
  val OperationsPing = 1.seconds

  def akkaStreamActorBehavior(
    stageInfo: Set[StageInfo],
    connectionStats: Set[ConnectionStats],
    streamName: Option[StageName],
    monitor: Option[ActorRef[PushMetrics.type]]
  ): Behavior[PushMetrics.type] = Behaviors.receive {
    case (ctx, PushMetrics) =>
      monitor.foreach(_ ! PushMetrics)
      EventBus(system).publishEvent(ActorInterpreterStats(ctx.self.toClassic, stageInfo, connectionStats, streamName))
      Behaviors.same
  }

  def cleanActors(refs: Seq[classic.ActorRef]): Unit = refs.foreach(_ ! PoisonPill)

  override protected def createMonitor: Monitor = {
    val operations = StreamOperatorMonitorTestProbe(OperationsPing)
    val global     = StreamMonitorTestProbe()
    (operations, global)
  }

  override protected val serviceKey: Option[ServiceKey[_]] = Some(streamServiceKey)

  override protected def setUp(monitor: Monitor, cache: Boolean): ActorRef[Command] = monitor match {
    case (operations, global) => system.systemActorOf(AkkaStreamMonitoring(operations, global, None), createUniqueId)
  }

  "AkkaStreamMonitoring" should "ask all received refs for metrics" in testWithRef {
    case ((_, _), sut) =>
      val probes = List.fill(5)(TestProbe[PushMetrics.type]())
      val refs = probes
        .map(probe => akkaStreamActorBehavior(Set.empty, Set.empty, None, Some(probe.ref)))
        .map(behavior => system.systemActorOf(behavior, createUniqueId).toClassic)

      sut ! StartStreamCollection(refs.toSet)

      forAll(probes)(_.expectMessage(PushMetrics))

      cleanActors(refs)
  }

  it should "publish amount of actors running stream" in testWithRef {
    case ((_, global), sut) =>
      val ExpectedCount = 5
      val refs = generateUniqueString(ExpectedCount, 10).zipWithIndex.map {
        case (name, index) =>
          val behavior = akkaStreamActorBehavior(Set.empty, Set.empty, None, None)
          system.systemActorOf(behavior, s"$name-$index-$index-${randomString(10)}").toClassic
      }

      sut ! StartStreamCollection(refs.toSet)

      global.streamActorsProbe.receiveMessage(2.seconds) shouldBe (MetricRecorded(ExpectedCount))


      cleanActors(refs)
  }

  it should "publish amount of running streams" in testWithRef {
    case ((_, global), sut) =>
      val ExpectedCount  = 5
      val ActorPerStream = 3
      val refs = generateUniqueString(ExpectedCount, 10).zipWithIndex.flatMap {
        case (name, index) =>
          val behavior = akkaStreamActorBehavior(Set.empty, Set.empty, None, None)
          List.tabulate(ActorPerStream) { streamId =>
            system.systemActorOf(behavior, s"$name-$index-$streamId-${randomString(10)}").toClassic
          }
      }

      sut ! StartStreamCollection(refs.toSet)

      global.runningStreamsProbe.receiveMessage(2.seconds) shouldBe (MetricRecorded(ExpectedCount))
      global.streamActorsProbe.receiveMessage(2.seconds) shouldBe (MetricRecorded(ExpectedCount * ActorPerStream))

      cleanActors(refs)
  }

  it should "collect amount of messages processed" in testWithRef {
    case ((operations, _), sut) =>
      val ExpectedCount = 5
      val refs = generateUniqueString(ExpectedCount, 10).zipWithIndex.map {
        case (name, index) =>


          val behavior = akkaStreamActorBehavior(Set.empty, Set.empty, None, None)


          system.systemActorOf(behavior, s"$name-$index-$index-${randomString(10)}").toClassic
      }
      sut ! StartStreamCollection(refs.toSet)


      cleanActors(refs)
  }

}
