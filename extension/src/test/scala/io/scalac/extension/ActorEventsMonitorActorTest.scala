package io.scalac.extension

import scala.concurrent.duration._

import akka.actor.PoisonPill
import akka.actor.testkit.typed.javadsl.FishingOutcomes
import akka.actor.testkit.typed.scaladsl.TestProbe
import akka.actor.typed.ActorRef
import akka.actor.typed.ActorSystem
import akka.actor.typed.Behavior
import akka.actor.typed.receptionist.ServiceKey
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.StashBuffer
import akka.util.Timeout

import org.scalatest.Inspectors
import org.scalatest.TestSuite
import org.scalatest.concurrent.ScaledTimeSpans
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers

import io.scalac.core.model._
import io.scalac.core.util.ActorPathOps
import io.scalac.extension.ActorEventsMonitorActor._
import io.scalac.extension.ActorEventsMonitorActorTest._
import io.scalac.extension.actor.ActorMetrics
import io.scalac.extension.actor.MutableActorMetricsStorage
import io.scalac.extension.event.ActorEvent.StashMeasurement
import io.scalac.extension.event.EventBus
import io.scalac.extension.metric.ActorMetricMonitor.Labels
import io.scalac.extension.util.AggMetric.LongValueAggMetric
import io.scalac.extension.util.TestCase._
import io.scalac.extension.util.probe.ActorMonitorTestProbe
import io.scalac.extension.util.probe.BoundTestProbe.MetricObserved
import io.scalac.extension.util.probe.BoundTestProbe.MetricObserverCommand
import io.scalac.extension.util.probe.BoundTestProbe.MetricRecorded
import io.scalac.extension.util.probe.ObserverCollector.ScheduledCollectorImpl

trait ActorEventMonitorActorTestConfig {
  this: TestSuite with ScaledTimeSpans =>

  protected val pingOffset: FiniteDuration     = scaled(1.seconds)
  protected val reasonableTime: FiniteDuration = 3 * pingOffset
  implicit val timeout: Timeout                = pingOffset
}

class ActorEventsMonitorActorTest
    extends AnyFlatSpecLike
    with Matchers
    with Inspectors
    with MonitorWithServiceTestCaseFactory
    with FreshActorSystemTestCaseFactory
    with ActorEventMonitorActorTestConfig {

  type Monitor          = ActorMonitorTestProbe
  override type Context = TestContext[Monitor]

  protected val serviceKey: ServiceKey[_] = actorServiceKey

  protected def createMonitor(implicit system: ActorSystem[_]): ActorMonitorTestProbe =
    ActorMonitorTestProbe(new ScheduledCollectorImpl(pingOffset))

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

  protected def createContextFromMonitor(monitor: ActorMonitorTestProbe)(implicit
    system: ActorSystem[_]
  ): TestContext[ActorMonitorTestProbe] =
    new TestContext[Monitor](monitor)

  override protected def testSameOrParent(ref: ActorRef[_], parent: ActorRef[_]): Boolean =
    ActorPathOps.getPathString(ref).startsWith(ActorPathOps.getPathString(parent))

  private val CommonLabels: Labels = Labels("/")

  "ActorEventsMonitor" should "record mailbox size" in testCase { implicit c =>
    shouldObserveWithChange(monitor.mailboxSizeProbe, CommonLabels, c.fakeMailboxSize, c.fakeMailboxSize += 1)
  }

  it should "dead actors should not report" in testCase { implicit c =>
    val tmp    = system.systemActorOf[Nothing](Behaviors.ignore, createUniqueId)
    val labels = Labels(ActorPathOps.getPathString(tmp))

    shouldObserve(monitor.mailboxSizeProbe, labels, c.fakeMailboxSize)

    tmp.unsafeUpcast[Any] ! PoisonPill
    monitor.mailboxSizeProbe.expectTerminated(tmp)

    assertThrows[AssertionError]( //TODO fix this to better implementation
      monitor.mailboxSizeProbe.fishForMessage(reasonableTime) {
        case MetricObserved(_, `labels`) =>
          FishingOutcomes.complete()
        case _ => FishingOutcomes.continueAndIgnore()
      }
    )
  }

  it should "record stash size" in testCase { implicit c =>
    val stashActor = system.systemActorOf(StashActor(10), "stashActor")
    val actorPath  = ActorPathOps.getPathString(stashActor)

    def stashMeasurement(size: Int): Unit =
      EventBus(system).publishEvent(StashMeasurement(size, actorPath))

    stashActor ! Message("random")
    stashMeasurement(1)
    monitor.stashSizeProbe.expectMessage(MetricRecorded(1))
    stashActor ! Message("42")
    stashMeasurement(2)
    monitor.stashSizeProbe.expectMessage(MetricRecorded(2))
    stashActor ! Open
    stashMeasurement(0)
    monitor.stashSizeProbe.expectMessage(MetricRecorded(0))
    stashActor ! Close
    stashActor ! Message("emanuel")
    stashMeasurement(1)
    monitor.stashSizeProbe.expectMessage(MetricRecorded(1))
    stashActor.unsafeUpcast[Any] ! PoisonPill
    monitor.stashSizeProbe.expectTerminated(stashActor)

  }

  it should "record avg mailbox time" in testCase { implicit c =>
    shouldObserveWithChange(
      monitor.mailboxTimeAvgProbe,
      CommonLabels,
      c.fakeMailboxTime.avg,
      c.fakeMailboxTime.copy(avg = c.fakeMailboxTime.avg + 1)
    )
  }

  it should "record min mailbox time" in testCase { implicit c =>
    shouldObserveWithChange(
      monitor.mailboxTimeMinProbe,
      CommonLabels,
      c.fakeMailboxTime.min,
      c.fakeMailboxTime.copy(min = c.fakeMailboxTime.min + 1)
    )
  }

  it should "record max mailbox time" in testCase { implicit c =>
    shouldObserveWithChange(
      monitor.mailboxTimeMaxProbe,
      CommonLabels,
      c.fakeMailboxTime.max,
      c.fakeMailboxTime.copy(max = c.fakeMailboxTime.max + 1)
    )
  }

  it should "record sum mailbox time" in testCase { implicit c =>
    shouldObserveWithChange(
      monitor.mailboxTimeSumProbe,
      CommonLabels,
      c.fakeMailboxTime.sum,
      c.fakeMailboxTime.copy(sum = c.fakeMailboxTime.sum + 1)
    )
  }

  it should "record received messages" in testCase { implicit c =>
    shouldObserveWithChange(
      monitor.receivedMessagesProbe,
      CommonLabels,
      c.fakeReceivedMessages,
      c.fakeReceivedMessages += 1
    )
  }

  it should "record processed messages" in testCase { implicit c =>
    shouldObserveWithChange(
      monitor.processedMessagesProbe,
      CommonLabels,
      c.fakeProcessedMessages,
      c.fakeProcessedMessages += 1
    )
  }

  it should "record failed messages" in testCase { implicit c =>
    shouldObserveWithChange(
      monitor.failedMessagesProbe,
      CommonLabels,
      c.fakeFailedMessages,
      c.fakeFailedMessages += 1
    )
  }

  it should "record avg processing time" in testCase { implicit c =>
    shouldObserveWithChange(
      monitor.processingTimeAvgProbe,
      CommonLabels,
      c.fakeProcessingTimes.avg,
      c.fakeProcessingTimes.copy(avg = c.fakeProcessingTimes.avg + 1)
    )
  }

  it should "record min processing time" in testCase { implicit c =>
    shouldObserveWithChange(
      monitor.processingTimeMinProbe,
      CommonLabels,
      c.fakeProcessingTimes.min,
      c.fakeProcessingTimes.copy(min = c.fakeProcessingTimes.min + 1)
    )
  }

  it should "record max processing time" in testCase { implicit c =>
    shouldObserveWithChange(
      monitor.processingTimeMaxProbe,
      CommonLabels,
      c.fakeProcessingTimes.max,
      c.fakeProcessingTimes.copy(max = c.fakeProcessingTimes.max + 1)
    )
  }

  it should "record sum processing time" in testCase { implicit c =>
    shouldObserveWithChange(
      monitor.processingTimeSumProbe,
      CommonLabels,
      c.fakeProcessingTimes.sum,
      c.fakeProcessingTimes.copy(sum = c.fakeProcessingTimes.sum + 1)
    )
  }

  it should "record the sent messages" in testCase { implicit c =>
    shouldObserveWithChange(monitor.sentMessagesProbe, CommonLabels, c.fakeSentMessages, c.fakeSentMessages += 1)
  }

  def shouldObserve(probe: TestProbe[MetricObserverCommand[Labels]], labels: Labels, metric: Long): Unit =
    probe
      .fishForMessage(reasonableTime) {
        case MetricObserved(_, `labels`) => FishingOutcomes.complete()
        case _                           => FishingOutcomes.continueAndIgnore()
      }
      .loneElement should be(MetricObserved(metric, labels))

  def shouldObserveWithChange(
    probe: TestProbe[MetricObserverCommand[Labels]],
    labels: Labels,
    metric: => Long,
    change: => Unit
  ): Unit = {
    shouldObserve(probe, labels, metric)
    change
    shouldObserve(probe, labels, metric)
  }
}

object ActorEventsMonitorActorTest {

  final case class TestContext[+M](monitor: M)(implicit val system: ActorSystem[_]) extends MonitorTestCaseContext[M] {

    val TestActorTreeTraverser: ActorTreeTraverser = ReflectiveActorTreeTraverser

    @volatile var fakeMailboxSize       = 10
    @volatile var fakeReceivedMessages  = 12
    @volatile var fakeProcessedMessages = 10
    @volatile var fakeFailedMessages    = 2
    @volatile var fakeSentMessages      = 10

    @volatile var fakeMailboxTime: LongValueAggMetric = LongValueAggMetric(1, 2, 1, 4, 3)

    @volatile var fakeProcessingTimes: LongValueAggMetric = LongValueAggMetric(1, 2, 1, 4, 3)

    def fakeUnhandledMessages: Long = fakeReceivedMessages - fakeProcessedMessages

    val TestActorMetricsReader: ActorMetricsReader = { _ =>
      Some(
        ActorMetrics(
          mailboxSize = Some(fakeMailboxSize),
          mailboxTime = Some(fakeMailboxTime),
          receivedMessages = Some(fakeReceivedMessages),
          unhandledMessages = Some(fakeUnhandledMessages),
          failedMessages = Some(fakeFailedMessages),
          processingTime = Some(fakeProcessingTimes),
          sentMessages = Some(fakeSentMessages)
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
