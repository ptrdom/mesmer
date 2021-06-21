package io.scalac.mesmer.core.akka

package object model {

  /**
   * Command signalling that actor should send accumulated metrics in reply
   */
  private[scalac] case object PushMetrics

}
