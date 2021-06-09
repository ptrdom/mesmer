package io.scalac.mesmer.extension

import java.util.concurrent.atomic.AtomicReference

import akka.actor.typed._
import akka.actor.typed.receptionist.Receptionist.Listing
import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.TimerScheduler
import akka.util.Timeout
import akka.{ actor => classic }
import org.slf4j.LoggerFactory

import scala.concurrent.duration._
import scala.util.Failure
import scala.util.Success

import io.scalac.mesmer.core.model.ActorKey
import io.scalac.mesmer.core.model.ActorRefDetails
import io.scalac.mesmer.core.model.Node
import io.scalac.mesmer.core.util.ActorCellOps
import io.scalac.mesmer.core.util.ActorPathOps
import io.scalac.mesmer.core.util.ActorRefOps
import io.scalac.mesmer.core.util.Timestamp
import io.scalac.mesmer.extension.ActorEventsMonitorActor._
import io.scalac.mesmer.extension.actor.ActorCellDecorator
import io.scalac.mesmer.extension.actor.ActorMetrics
import io.scalac.mesmer.extension.actor.MetricStorageFactory
import io.scalac.mesmer.extension.metric.ActorMetricsMonitor
import io.scalac.mesmer.extension.metric.ActorMetricsMonitor.Labels
import io.scalac.mesmer.extension.metric.MetricObserver.Result
import io.scalac.mesmer.extension.metric.SyncWith
import io.scalac.mesmer.extension.service.ActorTreeService
import io.scalac.mesmer.extension.service.ActorTreeService.Command.GetActorTree
import io.scalac.mesmer.extension.service.actorTreeServiceKey
import io.scalac.mesmer.extension.util.GenericBehaviors
import io.scalac.mesmer.extension.util.Tree.Tree
import io.scalac.mesmer.extension.util.Tree._
import io.scalac.mesmer.extension.util.TreeF

object ActorEventsMonitorActor {

  sealed trait Command
  private[ActorEventsMonitorActor] final case object StartActorsMeasurement                       extends Command
  private[ActorEventsMonitorActor] final case class MeasureActorTree(refs: Tree[ActorRefDetails]) extends Command
  private[ActorEventsMonitorActor] final case class ServiceListing(listing: Listing)              extends Command

  def apply(
    actorMonitor: ActorMetricsMonitor,
    node: Option[Node],
    pingOffset: FiniteDuration,
    storageFactory: MetricStorageFactory[ActorKey],
    actorMetricsReader: ActorMetricsReader = ReflectiveActorMetricsReader,
    timestampFactory: () => Timestamp
  ): Behavior[Command] =
    Behaviors.setup[Command] { ctx =>
      GenericBehaviors
        .waitForService(actorTreeServiceKey) { ref =>
          Behaviors.withTimers[Command] { scheduler =>
            new ActorEventsMonitorActor(
              ctx,
              actorMonitor,
              node,
              pingOffset,
              storageFactory,
              scheduler,
              actorMetricsReader,
              timestampFactory
            ).start(ref)
          }
        }
    }

  /**
   * This trait is not side-effect free - aggregation of metrics depend
   * on this to report metrics that changed only from last read - this is required
   * to account for disappearing actors
   */
  trait ActorMetricsReader {
    def read(actor: classic.ActorRef): Option[ActorMetrics]
  }

  object ReflectiveActorMetricsReader extends ActorMetricsReader {

    private val logger = LoggerFactory.getLogger(getClass)

    def read(actor: classic.ActorRef): Option[ActorMetrics] =
      for {
        cell    <- ActorRefOps.Local.cell(actor)
        metrics <- ActorCellDecorator.get(cell)
      } yield ActorMetrics(
        mailboxSize = safeRead(ActorCellOps.numberOfMessages(cell)),
        mailboxTime = metrics.mailboxTimeAgg.metrics,
        processingTime = metrics.processingTimeAgg.metrics,
        receivedMessages = Some(metrics.receivedMessages.take()),
        unhandledMessages = Some(metrics.unhandledMessages.take()),
        failedMessages = Some(metrics.failedMessages.take()),
        sentMessages = Some(metrics.sentMessages.take()),
        stashSize = metrics.stashSize.get(),
        droppedMessages = metrics.droppedMessages.map(_.take())
      )

    private def safeRead[T](value: => T): Option[T] =
      try Some(value)
      catch {
        case ex: Throwable =>
          logger.warn("Fail to read metric value", ex)
          None
      }

  }

}

private[extension] class ActorEventsMonitorActor private[extension] (
  context: ActorContext[Command],
  monitor: ActorMetricsMonitor,
  node: Option[Node],
  pingOffset: FiniteDuration,
  storageFactory: MetricStorageFactory[ActorKey],
  scheduler: TimerScheduler[Command],
  actorMetricsReader: ActorMetricsReader = ReflectiveActorMetricsReader,
  timestampFactory: () => Timestamp
) {

  import context._

  private[this] val boundMonitor = monitor.bind()

  private[this] val treeSnapshot = new AtomicReference[Option[Vector[(Labels, ActorMetrics)]]](None)

  @volatile
  private var lastCollectionTimestamp: Timestamp = timestampFactory()

  private def updateMetric(extractor: ActorMetrics => Option[Long])(result: Result[Long, Labels]): Unit = {
    val state = treeSnapshot.get()
    state
      .foreach(_.foreach { case (labels, metrics) =>
        extractor(metrics).foreach(value => result.observe(value, labels))
      })
  }

  // this is not idempotent!
  private def registerUpdaters(): Unit = {

    import boundMonitor._
    SyncWith()
      .`with`(mailboxSize)(updateMetric(_.mailboxSize))
      .`with`(failedMessages)(updateMetric(_.failedMessages))
      .`with`(processedMessages)(updateMetric(_.processedMessages))
      .`with`(receivedMessages)(updateMetric(_.receivedMessages))
      .`with`(mailboxTimeAvg)(updateMetric(_.mailboxTime.map(_.avg)))
      .`with`(mailboxTimeMax)(updateMetric(_.mailboxTime.map(_.max)))
      .`with`(mailboxTimeMin)(updateMetric(_.mailboxTime.map(_.min)))
      .`with`(mailboxTimeSum)(updateMetric(_.mailboxTime.map(_.sum)))
      .`with`(processingTimeAvg)(updateMetric(_.processingTime.map(_.avg)))
      .`with`(processingTimeMin)(updateMetric(_.processingTime.map(_.min)))
      .`with`(processingTimeMax)(updateMetric(_.processingTime.map(_.max)))
      .`with`(processingTimeSum)(updateMetric(_.processingTime.map(_.sum)))
      .`with`(sentMessages)(updateMetric(_.sentMessages))
      .`with`(stashSize)(updateMetric(_.stashSize))
      .`with`(droppedMessages)(updateMetric(_.droppedMessages))
      .afterAll {
        lastCollectionTimestamp = timestampFactory()
      }

  }

  // this is not idempotent
  def start(treeService: ActorRef[ActorTreeService.Command]): Behavior[Command] = {

    setTimeout()
    registerUpdaters()
    loop(treeService)
  }

  private def loop(actorService: ActorRef[ActorTreeService.Command]): Behavior[Command] = {
    implicit val timeout: Timeout = 2.seconds

    Behaviors.receiveMessagePartial[Command] {
      case StartActorsMeasurement =>
        context
          .ask[ActorTreeService.Command, Tree[ActorRefDetails]](actorService, adapter => GetActorTree(adapter)) {
            case Success(value) => MeasureActorTree(value)
            case Failure(_)     => StartActorsMeasurement // keep asking
          }
        Behaviors.same
      case MeasureActorTree(refs) =>
        update(refs)
        setTimeout() // loop
        Behaviors.same
    }
  }.receiveSignal { case (_, PreRestart | PostStop) =>
    boundMonitor.unbind()
    Behaviors.same
  }

  private def setTimeout(): Unit = scheduler.startSingleTimer(StartActorsMeasurement, pingOffset)

  private def update(refs: Tree[ActorRefDetails]): Unit = {

    val storage = refs.unfix.foldRight[storageFactory.Storage] {
      case TreeF(details, Vector()) =>
        import details._

        val storage = storageFactory.createStorage
        actorMetricsReader
          .read(ref)
          .fold(storage) { metric =>
            storage.save(ActorPathOps.getPathString(ref), metric, configuration.reporting.visible)
          }
      case TreeF(details, childrenMetrics) =>
        import details._

        val storage = childrenMetrics.reduce(storageFactory.mergeStorage)

        actorMetricsReader.read(ref).fold(storage) { currentMetrics =>
          import configuration.reporting._
          val actorKey = ActorPathOps.getPathString(ref)

          storage.save(actorKey, currentMetrics, visible)
          if (aggregate) {
            storage.compute(actorKey)
          } else storage
        }
    }

    captureState(storage)
  }

  private def captureState(storage: storageFactory.Storage): Unit = {
    log.debug("Capturing current actor tree state")

    val currentSnapshot = treeSnapshot.get().getOrElse(Vector.empty)
    val metrics = storage.iterable.map { case (key, metrics) =>
      currentSnapshot.find { case (labels, _) =>
        labels.actorPath == key
      }.fold((Labels(key, node), metrics)) { case (labels, existingMetrics) =>
        (labels, existingMetrics.sum(metrics))
      }
    }.toVector

    treeSnapshot.set(Some(metrics))
    log.trace("Current actor metrics state {}", metrics)
  }

}
