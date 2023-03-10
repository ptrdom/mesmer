package io.scalac.mesmer.otelextension.akka;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class MesmerAkkaHelpers {

  // Mesmer core helpers. With Muzzle plugin this can be safely defined in `isHelperClass`.
  public static List<String> coreHelpers() {
    return List.of(
        "io.scalac.mesmer.core.package$",
        "io.scalac.mesmer.core.PathMatcher$",
        "io.scalac.mesmer.core.PathMatcher$Error",
        "io.scalac.mesmer.core.PathMatcher$Prefix",
        "io.scalac.mesmer.core.PathMatcher",
        "io.scalac.mesmer.core.ActorGrouping$",
        "io.scalac.mesmer.core.ActorGrouping$Error",
        "io.scalac.mesmer.core.ActorGrouping",
        "io.scalac.mesmer.core.AkkaDispatcher$",
        "io.scalac.mesmer.core.ActorPathAttribute",
        "io.scalac.mesmer.core.Disabled",
        "io.scalac.mesmer.core.DisabledPath$",
        "io.scalac.mesmer.core.SomeActorPathAttribute",
        "io.scalac.mesmer.core.akka.model.AttributeNames$",
        "io.scalac.mesmer.core.akka.model.package$PushMetrics$",
        "io.scalac.mesmer.core.akka.stream.BidiFlowForward$",
        "io.scalac.mesmer.core.akka.stream.BidiFlowForward$$anon$1$$anon$2",
        "io.scalac.mesmer.core.akka.stream.BidiFlowForward$$anon$1",
        "io.scalac.mesmer.core.actor.ConfiguredAttributeFactory$$anonfun$1",
        "io.scalac.mesmer.core.event.AbstractEvent",
        "io.scalac.mesmer.core.event.AbstractService",
        "io.scalac.mesmer.core.event.ActorEvent$TagsSet",
        "io.scalac.mesmer.core.event.ActorEvent",
        "io.scalac.mesmer.core.event.EventBus$",
        "io.scalac.mesmer.core.event.EventBus",
        "io.scalac.mesmer.core.event.PersistenceEvent",
        "io.scalac.mesmer.core.event.ReceptionistBasedEventBus$Subscribers",
        "io.scalac.mesmer.core.event.ReceptionistBasedEventBus$",
        "io.scalac.mesmer.core.event.ReceptionistBasedEventBus",
        "io.scalac.mesmer.core.event.Service$",
        "io.scalac.mesmer.core.event.Service$$anon$1",
        "io.scalac.mesmer.core.event.Service",
        "io.scalac.mesmer.core.event.StreamEvent$StreamInterpreterStats",
        "io.scalac.mesmer.core.event.StreamEvent",
        "io.scalac.mesmer.core.util.ClassicActorSystemOps$",
        "io.scalac.mesmer.core.util.ClassicActorSystemOps$ActorSystemOps$",
        "io.scalac.mesmer.core.util.Interval$",
        "io.scalac.mesmer.core.util.Interval",
        "io.scalac.mesmer.core.util.MutableTypedMap$",
        "io.scalac.mesmer.core.util.MutableTypedMap",
        "io.scalac.mesmer.core.util.Retry$",
        "io.scalac.mesmer.core.util.stream$",
        "io.scalac.mesmer.core.util.TypedActorSystemOps$",
        "io.scalac.mesmer.core.config.ConfigurationUtils$",
        "io.scalac.mesmer.core.config.ConfigurationUtils$ConfigOps$",
        "io.scalac.mesmer.core.invoke.Lookup",
        "io.scalac.mesmer.core.model.ActorRefTags$ActorRefNonEmptyTags",
        "io.scalac.mesmer.core.model.ActorRefTags$",
        "io.scalac.mesmer.core.model.ActorRefTags",
        "io.scalac.mesmer.core.model.Reporting$Group$",
        "io.scalac.mesmer.core.model.Reporting$Instance$",
        "io.scalac.mesmer.core.model.Reporting$Disabled$",
        "io.scalac.mesmer.core.model.Reporting$",
        "io.scalac.mesmer.core.model.Reporting",
        "io.scalac.mesmer.core.model.Tag$StreamName$",
        "io.scalac.mesmer.core.model.Tag$StreamName",
        "io.scalac.mesmer.core.model.Tag$SubStreamName$StreamNameWithIsland",
        "io.scalac.mesmer.core.model.Tag$SubStreamName$",
        "io.scalac.mesmer.core.model.Tag$All$",
        "io.scalac.mesmer.core.model.Tag$SubStreamName",
        "io.scalac.mesmer.core.model.Tag$StageName$",
        "io.scalac.mesmer.core.model.Tag$StageName",
        "io.scalac.mesmer.core.model.Tag$StageName$StreamUniqueStageName",
        "io.scalac.mesmer.core.model.Tag$StreamTag$",
        "io.scalac.mesmer.core.model.Tag$StreamName$StreamNameImpl",
        "io.scalac.mesmer.core.model.Tag$TerminatedTag$",
        "io.scalac.mesmer.core.model.Tag$",
        "io.scalac.mesmer.core.model.Tag",
        "io.scalac.mesmer.core.model.stream.StageInfo",
        "io.scalac.mesmer.core.model.stream.ConnectionStats");
  }

  public static List<String> combine(List<String>... values) {
    return Stream.of(values).flatMap(Collection::stream).collect(Collectors.toList());
  }
}
