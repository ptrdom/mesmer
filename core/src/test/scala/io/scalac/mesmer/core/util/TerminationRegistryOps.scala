package io.scalac.mesmer.core.util

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.AskPattern._
import org.scalatest.BeforeAndAfterAll
import org.scalatest.Suite
import org.scalatest.concurrent.ScalaFutures

import scala.concurrent.duration._
import scala.language.postfixOps

import io.scalac.mesmer.core.util.TerminationRegistry.Ack
import io.scalac.mesmer.core.util.TerminationRegistry.WaitForTermination

trait TerminationRegistryOps extends ScalaTestWithActorTestKit with BeforeAndAfterAll with ScalaFutures {
  this: Suite =>

  private var _registry: Option[ActorRef[TerminationRegistry.Command]] = None

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    _registry = Some(system.systemActorOf(TerminationRegistry(), "terminationRegistry"))
  }

  def watch(actorRef: ActorRef[_]): Unit =
    _registry.foreach(_ ! TerminationRegistry.Watch(actorRef, None))

  def waitFor(actorRef: ActorRef[_]): Unit = _registry.foreach { registry =>
    val result = registry.ask[Ack](reply => WaitForTermination(actorRef, reply))
    assert(result.isReadyWithin(2 second))
  }

  def unwatchAll(): Unit = _registry.foreach(_ ! TerminationRegistry.UnwatchAll)

  override protected def afterAll(): Unit = {
    unwatchAll()
    super.afterAll()
  }
}
