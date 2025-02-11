package io.scalac.mesmer.core.util

import akka.actor.PoisonPill
import akka.actor.typed.ActorRef
import akka.actor.typed.ActorSystem
import akka.actor.typed.Behavior
import akka.actor.typed.receptionist.ServiceKey
import akka.actor.typed.scaladsl.Behaviors
import akka.testkit.TestKit
import akka.util.Timeout

import io.scalac.mesmer.core.tagging._
import io.scalac.mesmer.core.util.TestCase.MonitorWithServiceTestCaseFactory.SetupTag
import io.scalac.mesmer.core.util.probe.Collected
import io.scalac.mesmer.core.util.probe.ObserverCollector

object TestCase {

  /**
   * This i
   */
  trait TestCaseFactory {
    protected type Env
    protected type Context
    protected type Setup

    protected def startEnv(): Env
    protected def stopEnv(env: Env): Unit
    protected def createContext(env: Env): Context
    protected def setUp(context: Context): Setup
    protected def tearDown(setup: Setup): Unit

    // DSL

    final def testCaseWithSetupAndContext[C2 <: Context, T](hackContext: Context => C2)(tc: Setup => C2 => T): T = {
      val env = startEnv()
      try {
        val ctx   = hackContext(createContext(env))
        val setup = setUp(ctx)
        try {
          val result = Function.uncurried(tc)(setup, ctx)
          result
        } finally tearDown(setup)
      } finally stopEnv(env)
    }

    final def testCaseWith[C2 <: Context, T](mapContext: Context => C2)(tc: C2 => T): T =
      testCaseWithSetupAndContext(mapContext)(_ => tc)

    final def testCase[T](tc: Context => T): T =
      testCaseWith(identity)(tc)

    final def testCaseSetupContext[T](tc: Setup => Context => T): T =
      testCaseWithSetupAndContext(identity)(tc)

    final def testCaseSetup[T](tc: Setup => T): T =
      testCaseWithSetupAndContext(identity)(setup => _ => tc(setup))
  }

  // Test Impl

  trait ActorSystemEnvTestCaseFactory extends TestCaseFactory {
    type Env = ActorSystem[_]
  }

  trait FreshActorSystemTestCaseFactory extends ActorSystemEnvTestCaseFactory with TestOps {

    // overrides
    protected final def startEnv(): ActorSystem[_] =
      ActorSystem[Nothing](Behaviors.ignore, createUniqueId, TestConfig.localActorProvider)
    protected final def stopEnv(env: ActorSystem[_]): Unit = TestKit.shutdownActorSystem(env.classicSystem)

    // DSL
    implicit def system(implicit context: MonitorTestCaseContext[_]): ActorSystem[_] = context.system
  }

  trait ProvidedActorSystemTestCaseFactory extends ActorSystemEnvTestCaseFactory {

    // add-on api
    implicit protected def system: ActorSystem[_]

    // overrides
    protected final def startEnv(): ActorSystem[_]         = system
    protected final def stopEnv(env: ActorSystem[_]): Unit = {}
  }

  trait MonitorTestCaseContext[+M] {
    val monitor: M
    implicit val system: ActorSystem[_]
  }

  object MonitorTestCaseContext {
    final case class BasicContext[+M](monitor: M, caching: Boolean = false)(implicit val system: ActorSystem[_])
        extends MonitorTestCaseContext[M] {
      def withCaching: BasicContext[M] = copy(caching = true)
    }
  }

  trait AbstractMonitorTestCaseFactory extends ActorSystemEnvTestCaseFactory {
    type Monitor
    type Context <: MonitorTestCaseContext[Monitor]

    // add-on api
    protected def createMonitor(implicit system: ActorSystem[_]): Monitor
    protected def createContextFromMonitor(monitor: Monitor)(implicit system: ActorSystem[_]): Context

    // overrides
    protected final def createContext(env: ActorSystem[_]): Context =
      createContextFromMonitor(createMonitor(env))(env)

    // DSL
    def monitor(implicit context: Context): Monitor = context.monitor

    def collector(implicit context: Context, ev: Monitor <:< Collected): ObserverCollector = ev(monitor).collector
  }

  trait MonitorWithActorRefSetupTestCaseFactory extends AbstractMonitorTestCaseFactory with TestOps {
    type Command
    type Setup = ActorRef[_ >: Command] @@ SetupTag

    // add-on api
    protected def createMonitorBehavior(implicit context: Context): Behavior[Command]

    // overrides
    protected def setUp(context: Context): Setup = {
      val monitorBehavior = createMonitorBehavior(context)
      val monitorActor    = context.system.systemActorOf(monitorBehavior, createUniqueId)
      monitorActor.taggedWith[SetupTag]
    }

    final protected def tearDown(setup: Setup): Unit =
      setup.unsafeUpcast[Any] ! PoisonPill
  }

  object MonitorWithActorRefSetupTestCaseFactory {
    sealed trait SetupTag
  }

  trait MonitorWithServiceTestCaseFactory extends MonitorWithActorRefSetupTestCaseFactory with ReceptionistOps {

    protected val serviceKey: ServiceKey[_]
    implicit def timeout: Timeout

    // overrides
    final override protected def setUp(context: Context): Setup = {
      val monitorActor = super[MonitorWithActorRefSetupTestCaseFactory].setUp(context)
      onlyRef(monitorActor, serviceKey)(context.system, timeout)
      monitorActor.taggedWith[SetupTag]
    }

  }

  object MonitorWithServiceTestCaseFactory {
    sealed trait SetupTag

  }

  import MonitorTestCaseContext.BasicContext

  trait MonitorWithBasicContextTestCaseFactory extends AbstractMonitorTestCaseFactory {
    type Context = BasicContext[Monitor]
    // overrides
    final protected def createContextFromMonitor(
      monitor: Monitor
    )(implicit system: ActorSystem[_]): BasicContext[Monitor] =
      BasicContext(monitor)
  }

  trait NoSetupTestCaseFactory extends TestCaseFactory {
    type Setup = Unit
    protected def tearDown(setup: Setup): Unit  = ()
    protected def setUp(context: Context): Unit = ()
  }

  // common types as aliases...
  // basic context + service + provided
  trait CommonMonitorTestFactory
      extends MonitorWithBasicContextAndServiceTestCaseFactory
      with ProvidedActorSystemTestCaseFactory

  trait MonitorWithBasicContextAndServiceTestCaseFactory
      extends MonitorWithBasicContextTestCaseFactory
      with MonitorWithServiceTestCaseFactory

}
