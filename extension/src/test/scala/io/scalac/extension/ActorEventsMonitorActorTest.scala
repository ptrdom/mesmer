package io.scalac.extension

import scala.concurrent.duration._

import akka.actor.PoisonPill
import akka.actor.testkit.typed.scaladsl.TestProbe
import akka.actor.typed.receptionist.ServiceKey
import akka.actor.typed.scaladsl.{ Behaviors, StashBuffer }
import akka.actor.typed.{ ActorRef, ActorSystem, Behavior }
import akka.util.Timeout

import org.scalatest.Inspectors
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers

import io.scalac.core.model._
import io.scalac.core.util.ActorPathOps
import io.scalac.extension.ActorEventsMonitorActor._
import io.scalac.extension.ActorEventsMonitorActorTest._
import io.scalac.extension.actor.{ ActorMetrics, MutableActorMetricsStorage }
import io.scalac.extension.event.ActorEvent.StashMeasurement
import io.scalac.extension.event.EventBus
import io.scalac.extension.metric.ActorMetricMonitor.Labels
import io.scalac.extension.util.AggMetric.LongValueAggMetric
import io.scalac.extension.util.TestCase._
import io.scalac.extension.util.TimeSeries.LongTimeSeries
import io.scalac.extension.util.probe.ActorMonitorTestProbe
import io.scalac.extension.util.probe.ActorMonitorTestProbe.TestBoundMonitor
import io.scalac.extension.util.probe.BoundTestProbe.{ MetricObserved, MetricObserverCommand, MetricRecorded }
import io.scalac.extension.util.probe.ObserverCollector.CommonCollectorImpl

class ActorEventsMonitorActorTest
    extends AnyFlatSpecLike
    with Matchers
    with Inspectors
    with MonitorWithServiceTestCaseFactory
    with FreshActorSystemTestCaseFactory {

  type Monitor          = ActorMonitorTestProbe
  override type Context = TestContext[Monitor]

  private val pingOffset: FiniteDuration     = 1.seconds
  private val reasonableTime: FiniteDuration = 3 * pingOffset
  implicit def timeout: Timeout              = pingOffset

  protected val serviceKey: ServiceKey[_] = actorServiceKey

  protected def createMonitor(implicit system: ActorSystem[_]): ActorMonitorTestProbe =
    new ActorMonitorTestProbe(new CommonCollectorImpl(pingOffset))

  protected def createMonitorBehavior(implicit
    c: TestContext[Monitor]
  ): Behavior[_] =
    ActorEventsMonitorActor(
      monitor,
      None,
      pingOffset,
      MutableActorMetricsStorage.empty,
      system.systemActorOf(Behaviors.ignore[AkkaStreamMonitoring.Command], createUniqueId),
      actorMetricsReader = c.TestActorMetricsReader,
      actorTreeTraverser = c.TestActorTreeTraverser
    )

  override protected def createContextFromMonitor(monitor: ActorMonitorTestProbe)(implicit
    system: ActorSystem[_]
  ): TestContext[ActorMonitorTestProbe] =
    new TestContext[Monitor](monitor)

  override protected def testSameOrParent(ref: ActorRef[_], parent: ActorRef[_]): Boolean =
    ActorPathOps.getPathString(ref).startsWith(ActorPathOps.getPathString(parent))

  "ActorEventsMonitor" should "record mailbox size" in testCase { implicit c =>
    val bound = monitor.bind(Labels("/"))
    recordMailboxSize(5, bound)
    bound.unbind()
  }

  it should "record mailbox size changes" in testCase { implicit c =>
    val bound = monitor.bind(Labels("/"))
    recordMailboxSize(5, bound)
    recordMailboxSize(10, bound)
    bound.unbind()
  }

  it should "dead actors should not report" in testCase { implicit c =>
    val tmp   = system.systemActorOf[Nothing](Behaviors.ignore, "tmp")
    val bound = monitor.bind(Labels(ActorPathOps.getPathString(tmp)))
    recordMailboxSize(5, bound)
    tmp.unsafeUpcast[Any] ! PoisonPill
    bound.mailboxSizeProbe.expectTerminated(tmp)
    bound.mailboxSizeProbe.expectNoMessage(reasonableTime)
    bound.unbind()
  }

  it should "record stash size" in testCase { implicit c =>
    val stashActor = system.systemActorOf(StashActor(10), "stashActor")
    val bound      = monitor.bind(Labels(ActorPathOps.getPathString(stashActor)))
    def stashMeasurement(size: Int): Unit =
      EventBus(system).publishEvent(StashMeasurement(size, ActorPathOps.getPathString(stashActor)))
    stashActor ! Message("random")
    stashMeasurement(1)
    bound.stashSizeProbe.expectMessage(MetricRecorded(1))
    stashActor ! Message("42")
    stashMeasurement(2)
    bound.stashSizeProbe.expectMessage(MetricRecorded(2))
    stashActor ! Open
    stashMeasurement(0)
    bound.stashSizeProbe.expectMessage(MetricRecorded(0))
    stashActor ! Close
    stashActor ! Message("emanuel")
    stashMeasurement(1)
    bound.stashSizeProbe.expectMessage(MetricRecorded(1))
    stashActor.unsafeUpcast[Any] ! PoisonPill
    bound.stashSizeProbe.expectTerminated(stashActor)
    bound.unbind()
  }

  it should "record avg mailbox time" in testCase { implicit c =>
    val bound = monitor.bind(Labels("/"))
    shouldObserveTime(c.FakeMailboxTimeMs, bound.mailboxTimeAvgProbe)
    bound.unbind()
  }

  it should "record min mailbox time" in testCase { implicit c =>
    val bound = monitor.bind(Labels("/"))
    shouldObserveTime(c.FakeMailboxTimeMs / 2, bound.mailboxTimeMinProbe)
    bound.unbind()
  }

  it should "record max mailbox time" in testCase { implicit c =>
    val bound = monitor.bind(Labels("/"))
    shouldObserveTime(c.FakeMailboxTimeMs * 2, bound.mailboxTimeMaxProbe)
    bound.unbind()
  }

  it should "record sum mailbox time" in testCase { implicit c =>
    val bound = monitor.bind(Labels("/"))
    shouldObserveTime(c.FakeMailboxTimes.sum, bound.mailboxTimeSumProbe)
    bound.unbind()
  }

  it should "record received messages" in testCase { implicit c =>
    val bound = monitor.bind(Labels("/"))
    bound.receivedMessagesProbe.expectMessage(reasonableTime, MetricObserved(c.FakeReceivedMessages))
    bound.unbind()
  }

  it should "record processed messages" in testCase { implicit c =>
    val bound = monitor.bind(Labels("/"))
    bound.processedMessagesProbe.expectMessage(reasonableTime, MetricObserved(c.FakeProcessedMessages))
    bound.unbind()
  }

  it should "record failed messages" in testCase { implicit c =>
    val bound = monitor.bind(Labels("/"))
    bound.failedMessagesProbe.expectMessage(reasonableTime, MetricObserved(c.FakeFailedMessages))
    bound.unbind()
  }

  it should "record avg processing time" in testCase { implicit c =>
    val bound = monitor.bind(Labels("/"))
    shouldObserveTime(c.FakeProcessingTimeMs, bound.processingTimeAvgProbe)
    bound.unbind()
  }

  it should "record min processing time" in testCase { implicit c =>
    val bound = monitor.bind(Labels("/"))
    shouldObserveTime(c.FakeProcessingTimeMs / 2, bound.processingTimeMinProbe)
    bound.unbind()
  }

  it should "record max processing time" in testCase { implicit c =>
    val bound = monitor.bind(Labels("/"))
    shouldObserveTime(c.FakeProcessingTimeMs * 2, bound.processingTimeMaxProbe)
    bound.unbind()
  }

  it should "record sum processing time" in testCase { implicit c =>
    val bound = monitor.bind(Labels("/"))
    shouldObserveTime(c.FakeProcessingTimes.sum, bound.processingTimeSumProbe)
    bound.unbind()
  }

  def recordMailboxSize(n: Int, bound: TestBoundMonitor)(implicit c: TestContext[Monitor]): Unit = {
    c.FakeMailboxSize = n
    bound.mailboxSizeProbe.expectMessage(reasonableTime, MetricObserved(n))
  }

  def shouldObserveTime(durationMs: Long, probe: TestProbe[MetricObserverCommand]): Unit =
    probe.expectMessage(reasonableTime, MetricObserved(durationMs))

}

object ActorEventsMonitorActorTest {

  final case class TestContext[+M](monitor: M)(implicit val system: ActorSystem[_]) extends MonitorTestCaseContext[M] {
    var FakeMailboxSize = 10

    val TestActorTreeTraverser: ActorTreeTraverser = ReflectiveActorTreeTraverser
    val FakeMailboxTimeMs                          = 1000L
    val FakeMailboxTimes: Array[Long]              = Array(FakeMailboxTimeMs / 2, FakeMailboxTimeMs / 2, 2 * FakeMailboxTimeMs)
    // min: FakeMailboxTime / 2  |  avg: FakeMailboxTime  |  max: 2 * MailboxTime
    val FakeReceivedMessages       = 12
    val FakeProcessedMessages      = 10
    val FakeUnhandledMessages: Int = FakeReceivedMessages - FakeProcessedMessages
    val FakeFailedMessages         = 2
    val FakeProcessingTimeMs       = 100L
    val FakeProcessingTimes: Array[Long] =
      Array(FakeProcessingTimeMs / 2, FakeProcessingTimeMs / 2, 2 * FakeProcessingTimeMs)
    // min: FakeProcessingTime / 2  |  avg: FakeProcessingTime  |  max: 2 * FakeProcessingTime

    val TestActorMetricsReader: ActorMetricsReader = { _ =>
      Some(
        ActorMetrics(
          mailboxSize = Some(FakeMailboxSize),
          mailboxTime = Some(LongValueAggMetric.fromTimeSeries(new LongTimeSeries(FakeMailboxTimes))),
          receivedMessages = Some(FakeReceivedMessages),
          unhandledMessages = Some(FakeUnhandledMessages),
          failedMessages = Some(FakeFailedMessages),
          processingTime = Some(LongValueAggMetric.fromTimeSeries(new LongTimeSeries(FakeProcessingTimes)))
        )
      )
    }

  }

  sealed trait Command
  final case object Open                 extends Command
  final case object Close                extends Command
  final case class Message(text: String) extends Command

  object StashActor {
    def apply(capacity: Int): Behavior[Command] =
      Behaviors.withStash(capacity)(buffer => new StashActor(buffer).closed())
  }

  class StashActor(buffer: StashBuffer[Command]) {
    private def closed(): Behavior[Command] =
      Behaviors.receiveMessagePartial {
        case Open =>
          buffer.unstashAll(open())
        case msg =>
          buffer.stash(msg)
          Behaviors.same
      }

    private def open(): Behavior[Command] = Behaviors.receiveMessagePartial {
      case Close =>
        closed()
      case Message(_) =>
        Behaviors.same
    }

  }

}
