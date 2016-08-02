package com.avsystem.commons
package redis.commands

import akka.util.ByteString
import com.avsystem.commons.misc.{NamedEnum, NamedEnumCompanion, Opt}
import com.avsystem.commons.redis.CommandEncoder.CommandArg
import com.avsystem.commons.redis._
import com.avsystem.commons.redis.exception.UnexpectedReplyException
import com.avsystem.commons.redis.protocol.{ArrayMsg, BulkStringMsg, IntegerMsg, RedisMsg}

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

trait ClusteredClusterApi extends ApiSubset {
  def clusterKeyslot(key: ByteString): Result[Int] =
    execute(ClusterKeyslot(key))
}

trait NodeClusterApi extends ClusteredClusterApi {
  def clusterAddslots(slots: Seq[Int]): Result[Unit] =
    execute(ClusterAddslots(slots))
  def clusterCountFailureReports(nodeId: NodeId): Result[Long] =
    execute(ClusterCountFailureReports(nodeId))
  def clusterCountkeysinslot(slot: Int): Result[Long] =
    execute(ClusterCountkeysinslot(slot))
  def clusterDelslots(slots: Seq[Int]): Result[Unit] =
    execute(ClusterDelslots(slots))
  def clusterFailover(option: Opt[FailoverOption]): Result[Unit] =
    execute(ClusterFailover(option))
  def clusterForget(nodeId: NodeId): Result[Unit] =
    execute(ClusterForget(nodeId))
  def clusterGetkeysinslot(slot: Int, count: Long): Result[Seq[ByteString]] =
    execute(ClusterGetkeysinslot(slot, count))
  def clusterInfo: Result[ClusterInfoReply] =
    execute(ClusterInfo)
  def clusterMeet(address: NodeAddress): Result[Unit] =
    execute(ClusterMeet(address))
  def clusterNodes: Result[Seq[NodeInfo]] =
    execute(ClusterNodes)
  def clusterReplicate(nodeId: NodeId): Result[Unit] =
    execute(ClusterReplicate(nodeId))
  def clusterReset(hard: Boolean = false): Result[Unit] =
    execute(ClusterReset(hard))
  def clusterSaveconfig: Result[Unit] =
    execute(ClusterSaveconfig)
  def clusterSetConfigEpoch(configEpoch: Long): Result[Unit] =
    execute(ClusterSetConfigEpoch(configEpoch))
  def clusterSetslot(slot: Int, subcommand: SetslotSubcommand): Result[Unit] =
    execute(ClusterSetslot(slot, subcommand))
  def clusterSlaves(nodeId: NodeId): Result[Seq[NodeInfo]] =
    execute(ClusterSlaves(nodeId))
  def clusterSlots: Result[Seq[SlotRangeMapping]] =
    execute(ClusterSlots)
}

trait ConnectionClusterApi extends NodeClusterApi {
  def readonly: Result[Unit] =
    execute(Readonly)
  def readwrite: Result[Unit] =
    execute(Readwrite)
}

case class ClusterAddslots(slots: Seq[Int]) extends RedisUnitCommand with NodeCommand {
  val encoded = encoder("CLUSTER", "ADDSLOTS").add(slots).result
}

case class ClusterCountFailureReports(nodeId: NodeId) extends RedisLongCommand with NodeCommand {
  val encoded = encoder("CLUSTER", "COUNT-FAILURE-REPORTS").add(nodeId.raw).result
}

case class ClusterCountkeysinslot(slot: Int) extends RedisLongCommand with NodeCommand {
  val encoded = encoder("CLUSTER", "COUNTKEYSINSLOT").add(slot).result
}

case class ClusterDelslots(slots: Seq[Int]) extends RedisUnitCommand with NodeCommand {
  val encoded = encoder("CLUSTER", "DELSLOTS").add(slots).result
}

case class ClusterFailover(option: Opt[FailoverOption]) extends RedisUnitCommand with NodeCommand {
  val encoded = encoder("CLUSTER", "FAILOVER").add(option).result
}

case class ClusterForget(nodeId: NodeId) extends RedisUnitCommand with NodeCommand {
  val encoded = encoder("CLUSTER", "FORGET").add(nodeId.raw).result
}

case class ClusterGetkeysinslot(slot: Int, count: Long) extends RedisBinarySeqCommand with NodeCommand {
  val encoded = encoder("CLUSTER", "GETKEYSINSLOT").add(slot).add(count).result
}

case object ClusterInfo extends RedisCommand[ClusterInfoReply] with NodeCommand {
  val encoded = encoder("CLUSTER", "INFO").result
  def decodeExpected = {
    case BulkStringMsg(clusterInfo) => ClusterInfoReply.parse(clusterInfo.utf8String)
  }
}

case class ClusterKeyslot(key: ByteString) extends RedisCommand[Int] with NodeCommand {
  val encoded = encoder("CLUSTER", "KEYSLOT").key(key).result
  def decodeExpected = {
    case IntegerMsg(value) => value.toInt
  }
}

case class ClusterMeet(address: NodeAddress) extends RedisUnitCommand with NodeCommand {
  val encoded = encoder("CLUSTER", "MEET").add(address.ip).add(address.port).result
}

case object ClusterNodes extends RedisCommand[Seq[NodeInfo]] with NodeCommand {
  val encoded = encoder("CLUSTER", "NODES").result
  def decodeExpected = {
    case BulkStringMsg(nodeInfos) =>
      nodeInfos.utf8String.split("\n").iterator.filter(_.nonEmpty).map(NodeInfo.parse).toIndexedSeq
  }
}

case class ClusterReplicate(nodeId: NodeId) extends RedisUnitCommand with NodeCommand {
  val encoded = encoder("CLUSTER", "REPLICATE").add(nodeId.raw).result
}

case class ClusterReset(hard: Boolean) extends RedisUnitCommand with NodeCommand {
  val encoded = encoder("CLUSTER", "RESET").addFlag("HARD", hard).result
}

case object ClusterSaveconfig extends RedisUnitCommand with NodeCommand {
  val encoded = encoder("CLUSTER", "SAVECONFIG").result
}

case class ClusterSetConfigEpoch(configEpoch: Long) extends RedisUnitCommand with NodeCommand {
  val encoded = encoder("CLUSTER", "SET-CONFIG-EPOCH").add(configEpoch).result
}

case class ClusterSetslot(slot: Int, subcommand: SetslotSubcommand) extends RedisUnitCommand with NodeCommand {
  val encoded = encoder("CLUSTER", "SETSLOT").add(slot).add(subcommand).result
}

case class ClusterSlaves(nodeId: NodeId) extends RedisCommand[Seq[NodeInfo]] with NodeCommand {
  val encoded = encoder("CLUSTER", "SLAVES").add(nodeId.raw).result
  def decodeExpected = {
    case BulkStringMsg(nodeInfos) =>
      nodeInfos.utf8String.split("\n").iterator.filter(_.nonEmpty).map(NodeInfo.parse).toIndexedSeq
  }
}

case object ClusterSlots extends RedisCommand[Seq[SlotRangeMapping]] with NodeCommand {
  val encoded = encoder("CLUSTER", "SLOTS").result
  def decodeExpected = {
    case ArrayMsg(elements) => elements.map {
      case ArrayMsg(IntegerMsg(from) +: IntegerMsg(to) +: (master: RedisMsg) +: (replicas: IndexedSeq[RedisMsg])) =>
        val range = SlotRange(from.toInt, to.toInt)
        def parseNode(rr: RedisMsg) = rr match {
          case ArrayMsg(IndexedSeq(BulkStringMsg(ip), IntegerMsg(port), _*)) =>
            NodeAddress(ip.utf8String, port.toInt)
          case msg =>
            throw new UnexpectedReplyException(s"bad entry in CLUSTER SLOTS reply: $msg")
        }
        SlotRangeMapping(range, parseNode(master), replicas.map(parseNode))
      case msg =>
        throw new UnexpectedReplyException(s"bad reply for CLUSTER SLOTS: $msg")
    }
  }
}

case object Readonly extends RedisUnitCommand with ConnectionCommand {
  val encoded = encoder("READONLY").result
}

case object Readwrite extends RedisUnitCommand with ConnectionCommand {
  val encoded = encoder("READWRITE").result
}

case object Asking extends UnsafeCommand {
  val encoded = encoder("ASKING").result
}

case class NodeId(raw: String) extends AnyVal

sealed abstract class FailoverOption(val name: String) extends NamedEnum
object FailoverOption extends NamedEnumCompanion[FailoverOption] {
  case object Force extends FailoverOption("FORCE")
  case object Takeover extends FailoverOption("TAKEOVER")

  val values: List[FailoverOption] = caseObjects
}

sealed trait SetslotSubcommand
object SetslotSubcommand {
  case class Migrating(destinationNodeId: NodeId) extends SetslotSubcommand
  case class Importing(sourceNodeId: NodeId) extends SetslotSubcommand
  case object Stable extends SetslotSubcommand
  case class Node(nodeId: NodeId) extends SetslotSubcommand

  implicit val SubcommandCommandArg: CommandArg[SetslotSubcommand] =
    CommandArg((encoder, arg) => arg match {
      case Migrating(NodeId(nodeId)) => encoder.add("MIGRATING").add(nodeId)
      case Importing(NodeId(nodeId)) => encoder.add("IMPORTING").add(nodeId)
      case Stable => encoder.add("STABLE")
      case Node(NodeId(nodeId)) => encoder.add("NODE").add(nodeId)
    })
}

case class ClusterInfoReply(
  stateOk: Boolean,
  slotsAssigned: Int,
  slotsOk: Int,
  slotsPfail: Int,
  slotsFail: Int,
  knownNodes: Int,
  size: Int,
  currentEpoch: Long,
  myEpoch: Long,
  statsMessagesSent: Long,
  statsMessagesReceived: Long
) {
  override def toString =
    s"""
       |cluster_state:${if (stateOk) "ok" else "fail"}
       |cluster_slots_assigned:$slotsAssigned
       |cluster_slots_ok:$slotsOk
       |cluster_slots_pfail:$slotsPfail
       |cluster_slots_fail:$slotsFail
       |cluster_known_nodes:$knownNodes
       |cluster_size:$size
       |cluster_current_epoch:$currentEpoch
       |cluster_my_epoch:$myEpoch
       |cluster_stats_messages_sent:$statsMessagesSent
       |cluster_stats_messages_received:$statsMessagesReceived
     """.stripMargin.trim
}
object ClusterInfoReply {
  def parse(clusterInfo: String): ClusterInfoReply = {
    val rawReply = mutable.HashMap() ++ clusterInfo.split("\r\n").iterator
      .filter(_.nonEmpty).map(_.split(':') match { case Array(field, value) => (field, value) })

    ClusterInfoReply(
      rawReply("cluster_state") == "ok",
      rawReply("cluster_slots_assigned").toInt,
      rawReply("cluster_slots_ok").toInt,
      rawReply("cluster_slots_pfail").toInt,
      rawReply("cluster_slots_fail").toInt,
      rawReply("cluster_known_nodes").toInt,
      rawReply("cluster_size").toInt,
      rawReply("cluster_current_epoch").toLong,
      rawReply("cluster_my_epoch").toLong,
      rawReply("cluster_stats_messages_sent").toLong,
      rawReply("cluster_stats_messages_received").toLong
    )
  }
}

case class NodeInfo(
  id: NodeId,
  address: NodeAddress,
  flags: NodeFlags,
  master: Opt[NodeId],
  pingSent: Long,
  pongRecv: Long,
  configEpoch: Long,
  connected: Boolean,
  slots: Seq[SlotRange],
  importingSlots: Seq[(Int, NodeId)],
  migratingSlots: Seq[(Int, NodeId)]
) {
  override def toString = {
    val slotReprs = slots.iterator.map(_.toString) ++
      importingSlots.iterator.map({ case (s, n) => s"$s-<-$n" }) ++
      migratingSlots.iterator.map({ case (s, n) => s"$s->-$n" })
    val slotsRepr = if (slotReprs.hasNext) slotReprs.mkString(" ", " ", "") else ""
    val linkState = if (connected) "connected" else "disconnected"
    s"$id $address $flags ${master.getOrElse("-")} $pingSent $pongRecv $configEpoch $linkState$slotsRepr"
  }
}

object NodeInfo {
  def parse(lineStr: String) = {
    val line = lineStr.split(' ')
    val Array(ip, portStr) = line(1).split(':')

    val slotRanges = new ArrayBuffer[SlotRange]
    val importingSlots = new ArrayBuffer[(Int, NodeId)]
    val migratingSlots = new ArrayBuffer[(Int, NodeId)]

    line.iterator.drop(8).foreach { str =>
      (str.indexOf("-<-"), str.indexOf("->-"), str.indexOf('-')) match {
        case (-1, -1, -1) =>
          val slot = str.toInt
          slotRanges += SlotRange(slot, slot)
        case (-1, -1, idx) =>
          slotRanges += SlotRange(str.take(idx).toInt, str.drop(idx + 1).toInt)
        case (idx, -1, _) =>
          importingSlots += ((str.take(idx).toInt, NodeId(str.drop(idx + 1))))
        case (-1, idx, _) =>
          migratingSlots += ((str.take(idx).toInt, NodeId(str.drop(idx + 1))))
      }
    }

    NodeInfo(
      NodeId(line(0)),
      NodeAddress(ip, portStr.toInt),
      NodeFlags(line(2)),
      Opt(line(3)).filter(_ != "-").map(NodeId),
      line(4).toLong,
      line(5).toLong,
      line(6).toLong,
      line(7) == "connected",
      slotRanges,
      importingSlots,
      migratingSlots
    )
  }
}

class NodeFlags(val raw: Int) extends AnyVal {

  import NodeFlags._

  def |(other: NodeFlags) = new NodeFlags(raw | other.raw)
  def &(other: NodeFlags) = new NodeFlags(raw & other.raw)
  def ^(other: NodeFlags) = new NodeFlags(raw ^ other.raw)
  def unary_~ : NodeFlags = new NodeFlags(~raw)

  def myself: Boolean = (this & Myself) != Noflags
  def master: Boolean = (this & Master) != Noflags
  def slave: Boolean = (this & Slave) != Noflags
  def pfail: Boolean = (this & Pfail) != Noflags
  def fail: Boolean = (this & Fail) != Noflags
  def handshake: Boolean = (this & Handshake) != Noflags
  def noaddr: Boolean = (this & Noaddr) != Noflags

  override def toString =
    if (this == Noflags) "noflags"
    else reprValuePairs.iterator
      .collect({ case (str, flags) if (this & flags) != Noflags => str })
      .mkString(",")
}

object NodeFlags {
  val Noflags = new NodeFlags(0)
  val Myself = new NodeFlags(1 << 0)
  val Master = new NodeFlags(1 << 1)
  val Slave = new NodeFlags(1 << 2)
  val Pfail = new NodeFlags(1 << 3)
  val Fail = new NodeFlags(1 << 4)
  val Handshake = new NodeFlags(1 << 5)
  val Noaddr = new NodeFlags(1 << 6)

  private val reprValuePairs = Seq(
    "myself" -> Myself,
    "master" -> Master,
    "slave" -> Slave,
    "fail?" -> Pfail,
    "fail" -> Fail,
    "handshake" -> Handshake,
    "noaddr" -> Noaddr
  )

  def apply(str: String): NodeFlags = {
    val flagSet = str.split(',').to[mutable.HashSet]
    reprValuePairs.foldLeft(Noflags) {
      case (res, (s, flags)) => if (flagSet(s)) res | flags else res
    }
  }
}

case class SlotRangeMapping(range: SlotRange, master: NodeAddress, replicas: Seq[NodeAddress]) {
  override def toString = s"slots: $range, master: $master, slaves: ${replicas.mkString(",")}"
}
case class SlotRange(start: Int, end: Int) {
  def toRange: Range = start to end
  def contains(slot: Int): Boolean = slot >= start && slot <= end
  override def toString = if (start == end) start.toString else s"$start-$end"
}
