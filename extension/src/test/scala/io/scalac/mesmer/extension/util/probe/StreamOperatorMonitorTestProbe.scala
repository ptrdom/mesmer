package io.scalac.mesmer.extension.util.probe

import akka.actor.testkit.typed.scaladsl.TestProbe
import akka.actor.typed.ActorSystem

import io.scalac.mesmer.core.util.probe.Collected
import io.scalac.mesmer.core.util.probe.ObserverCollector
import io.scalac.mesmer.extension.metric.StreamMetricsMonitor
import io.scalac.mesmer.extension.metric.StreamMetricsMonitor.Attributes
import io.scalac.mesmer.extension.metric.StreamMetricsMonitor.BoundMonitor
import io.scalac.mesmer.extension.metric.StreamOperatorMetricsMonitor
import io.scalac.mesmer.extension.util.probe.BoundTestProbe.HistogramCommand
import io.scalac.mesmer.extension.util.probe.BoundTestProbe.MetricObserverCommand

final case class StreamOperatorMonitorTestProbe(
  processedTestProbe: TestProbe[MetricObserverCommand[StreamOperatorMetricsMonitor.Attributes]],
  runningOperatorsTestProbe: TestProbe[MetricObserverCommand[StreamOperatorMetricsMonitor.Attributes]],
  demandTestProbe: TestProbe[MetricObserverCommand[StreamOperatorMetricsMonitor.Attributes]],
  collector: ObserverCollector
)(implicit val system: ActorSystem[_])
    extends StreamOperatorMetricsMonitor
    with Collected {

  def bind(): StreamOperatorMetricsMonitor.BoundMonitor = new StreamOperatorMetricsMonitor.BoundMonitor {
    val processedMessages = ObserverTestProbeWrapper(processedTestProbe, collector)
    val operators         = ObserverTestProbeWrapper(runningOperatorsTestProbe, collector)
    val demand            = ObserverTestProbeWrapper(demandTestProbe, collector)
    def unbind(): Unit    = ()
  }
}

object StreamOperatorMonitorTestProbe {
  def apply(collector: ObserverCollector)(implicit system: ActorSystem[_]): StreamOperatorMonitorTestProbe = {
    val processProbe =
      TestProbe[MetricObserverCommand[StreamOperatorMetricsMonitor.Attributes]]("akka_stream_processed_messages")
    val demandProbe =
      TestProbe[MetricObserverCommand[StreamOperatorMetricsMonitor.Attributes]]("akka_stream_demand")
    val runningOperators =
      TestProbe[MetricObserverCommand[StreamOperatorMetricsMonitor.Attributes]]("akka_stream_running_operators")

    StreamOperatorMonitorTestProbe(processProbe, demandProbe, runningOperators, collector)
  }
}

class StreamMonitorTestProbe(
  val runningStreamsProbe: TestProbe[HistogramCommand],
  val streamActorsProbe: TestProbe[HistogramCommand],
  val processedMessagesProbe: TestProbe[MetricObserverCommand[Attributes]],
  val collector: ObserverCollector
)(implicit val system: ActorSystem[_])
    extends StreamMetricsMonitor
    with Collected {
  def bind(attributes: StreamMetricsMonitor.EagerAttributes): StreamMetricsMonitor.BoundMonitor = new BoundMonitor {

    val runningStreamsTotal = RecorderTestProbeWrapper(runningStreamsProbe)

    val streamActorsTotal = RecorderTestProbeWrapper(streamActorsProbe)

    val streamProcessedMessages = ObserverTestProbeWrapper(processedMessagesProbe, collector)

    def unbind(): Unit = ()
  }
}

object StreamMonitorTestProbe {
  def apply(collector: ObserverCollector)(implicit system: ActorSystem[_]): StreamMonitorTestProbe = {
    val runningStream          = TestProbe[HistogramCommand]()
    val streamActorsProbe      = TestProbe[HistogramCommand]()
    val processedMessagesProbe = TestProbe[MetricObserverCommand[Attributes]]()
    new StreamMonitorTestProbe(runningStream, streamActorsProbe, processedMessagesProbe, collector)
  }
}
