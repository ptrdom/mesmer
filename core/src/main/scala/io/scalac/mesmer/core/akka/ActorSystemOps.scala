package akka

import _root_.io.scalac.mesmer.core.invoke.Lookup
import akka.actor.ActorSystem
import akka.actor.ActorSystemImpl
import akka.util.Unsafe

object ActorSystemOps extends Lookup {

  private val initializedOffset: Long =
    Unsafe.instance.objectFieldOffset(classOf[ActorSystemImpl].getDeclaredField("_initialized"))

  implicit final class ActorSystemOpsWrapper(private val system: ActorSystem) extends AnyVal {

    /**
     * Unsafe is used to ensure volatile semantics on field access
     * @return
     */
    def isInitialized: Boolean =
      Unsafe.instance.getBooleanVolatile(system, initializedOffset)

  }
}
