package io.scalac.mesmer.core.event

import io.scalac.mesmer.core.model._
import io.scalac.mesmer.core.util.Timestamp

trait AbstractEvent extends Any { self =>
  type Service >: self.type
}

sealed trait PersistenceEvent extends AbstractEvent {
  type Service = PersistenceEvent
}

object PersistenceEvent {
  sealed trait RecoveryEvent                                                                  extends PersistenceEvent
  case class RecoveryStarted(path: Path, persistenceId: PersistenceId, timestamp: Timestamp)  extends RecoveryEvent
  case class RecoveryFinished(path: Path, persistenceId: PersistenceId, timestamp: Timestamp) extends RecoveryEvent

  sealed trait PersistEvent extends PersistenceEvent
  case class SnapshotCreated(path: Path, persistenceId: PersistenceId, sequenceNr: Long, timestamp: Timestamp)
      extends PersistenceEvent
  case class PersistingEventStarted(path: Path, persistenceId: PersistenceId, sequenceNr: Long, timestamp: Timestamp)
      extends PersistEvent
  case class PersistingEventFinished(path: Path, persistenceId: PersistenceId, sequenceNr: Long, timestamp: Timestamp)
      extends PersistEvent
}
