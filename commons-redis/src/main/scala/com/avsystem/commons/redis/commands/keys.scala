package com.avsystem.commons
package redis.commands

import akka.util.ByteString
import com.avsystem.commons.misc.{NamedEnum, NamedEnumCompanion, Opt}
import com.avsystem.commons.redis.CommandEncoder.CommandArg
import com.avsystem.commons.redis._
import com.avsystem.commons.redis.exception.UnexpectedReplyException
import com.avsystem.commons.redis.protocol._

import scala.collection.mutable.ArrayBuffer

/**
  * Author: ghik
  * Created: 06/04/16.
  */
trait ClusteredKeysApi extends ApiSubset {
  def del(keys: Seq[ByteString]): Result[Long] =
    execute(Del(keys))
  def dump(key: ByteString): Result[Opt[ByteString]] =
    execute(Dump(key))
  def exists(keys: Seq[ByteString]): Result[Long] =
    execute(Exists(keys))
  def expire(key: ByteString, seconds: Long): Result[Boolean] =
    execute(Expire(key, seconds))
  def expireat(key: ByteString, timestamp: Long): Result[Boolean] =
    execute(Expireat(key, timestamp))
  def migrate(keys: Seq[ByteString], address: NodeAddress, destinationDb: Int,
    timeout: Long, copy: Boolean = false, replace: Boolean = false): Result[Boolean] =
    execute(Migrate(keys, address, destinationDb, timeout, copy, replace))

  def objectRefcount(key: ByteString): Result[Opt[Long]] =
    execute(ObjectRefcount(key))
  def objectEncoding(key: ByteString): Result[Opt[Encoding]] =
    execute(ObjectEncoding(key))
  def objectIdletime(key: ByteString): Result[Opt[Long]] =
    execute(ObjectIdletime(key))

  def persist(key: ByteString): Result[Boolean] =
    execute(Persist(key))
  def pexpire(key: ByteString, milliseconds: Long): Result[Boolean] =
    execute(Pexpire(key, milliseconds))
  def pexpireat(key: ByteString, millisecondsTimestamp: Long): Result[Boolean] =
    execute(Pexpireat(key, millisecondsTimestamp))
  def pttl(key: ByteString): Result[Opt[Opt[Long]]] =
    execute(Pttl(key))

  def rename(key: ByteString, newkey: ByteString): Result[Unit] =
    execute(Rename(key, newkey))
  def renamenx(key: ByteString, newkey: ByteString): Result[Boolean] =
    execute(Renamenx(key, newkey))
  def restore(key: ByteString, ttl: Long, serializedValue: ByteString, replace: Boolean = false): Result[Unit] =
    execute(Restore(key, ttl, serializedValue, replace))

  def sort(key: ByteString, by: Opt[SortPattern] = Opt.Empty, limit: Opt[SortLimit] = Opt.Empty,
    asc: Boolean = true, alpha: Boolean = false): Result[Seq[ByteString]] =
    execute(Sort(key, by, limit, asc, alpha))
  def sortGet(key: ByteString, gets: Seq[SortPattern], by: Opt[SortPattern] = Opt.Empty, limit: Opt[SortLimit] = Opt.Empty,
    asc: Boolean = true, alpha: Boolean = false): Result[Seq[Seq[Opt[ByteString]]]] =
    execute(SortGet(key, gets, by, limit, asc, alpha))
  def sortStore(key: ByteString, destination: ByteString, by: Opt[SortPattern] = Opt.Empty, limit: Opt[SortLimit] = Opt.Empty,
    gets: Seq[SortPattern] = Nil, asc: Boolean = true, alpha: Boolean = false): Result[Long] =
    execute(SortStore(key, destination, by, limit, gets, asc, alpha))

  def ttl(key: ByteString): Result[Opt[Opt[Long]]] =
    execute(Ttl(key))
  def `type`(key: ByteString): Result[RedisType] =
    execute(Type(key))
}

trait NodeKeysApi extends ClusteredKeysApi with ApiSubset {
  def move(key: ByteString, db: Int): Result[Boolean] =
    execute(Move(key, db))
  def keys(pattern: ByteString): Result[Seq[ByteString]] =
    execute(Keys(pattern))
  def scan(cursor: Cursor, matchPattern: Opt[ByteString] = Opt.Empty, count: Opt[Long] = Opt.Empty): Result[(Cursor, Seq[ByteString])] =
    execute(Scan(cursor, matchPattern, count))
  def randomkey: Result[Opt[ByteString]] =
    execute(Randomkey)
  def wait(numslaves: Int, timeout: Long): Result[Long] =
    execute(Wait(numslaves, timeout))
}

case class Del(keys: Seq[ByteString]) extends RedisLongCommand with NodeCommand {
  require(keys.nonEmpty, "DEL requires at least one key")
  val encoded = encoder("DEL").keys(keys).result
}

case class Dump(key: ByteString) extends RedisOptBinaryCommand with NodeCommand {
  val encoded = encoder("DUMP").key(key).result
}

case class Exists(keys: Seq[ByteString]) extends RedisLongCommand with NodeCommand {
  require(keys.nonEmpty, "EXISTS requires at least one key")
  val encoded = encoder("EXISTS").keys(keys).result
}

case class Expire(key: ByteString, seconds: Long) extends RedisBooleanCommand with NodeCommand {
  val encoded = encoder("EXPIRE").key(key).add(seconds).result
}

case class Expireat(key: ByteString, timestamp: Long) extends RedisBooleanCommand with NodeCommand {
  val encoded = encoder("EXPIREAT").key(key).add(timestamp).result
}

case class Keys(pattern: ByteString) extends RedisBinarySeqCommand with NodeCommand {
  val encoded = encoder("KEYS").add(pattern).result
}

case class Migrate(keys: Seq[ByteString], address: NodeAddress, destinationDb: Int,
  timeout: Long, copy: Boolean, replace: Boolean) extends RedisCommand[Boolean] with NodeCommand {
  require(keys.nonEmpty, "MIGRATE requires at least one key")

  private val multiKey = keys.size > 1

  val encoded = {
    val enc = encoder("MIGRATE").add(address.ip).add(address.port)
    if (multiKey) {
      enc.add("")
    } else {
      enc.add(keys.head)
    }
    enc.add(destinationDb).add(timeout).addFlag("COPY", copy).addFlag("REPLACE", replace)
    if (multiKey) {
      enc.add("KEYS").keys(keys)
    }
    enc.result
  }

  def decodeExpected = {
    case SimpleStringStr("OK") => true
    case SimpleStringStr("NOKEY") => false
  }
}

case class Move(key: ByteString, db: Int) extends RedisBooleanCommand with NodeCommand {
  val encoded = encoder("MOVE").key(key).add(db).result
}

case class ObjectRefcount(key: ByteString) extends RedisOptLongCommand with NodeCommand {
  val encoded = encoder("OBJECT", "REFCOUNT").key(key).result
}

case class ObjectEncoding(key: ByteString) extends RedisCommand[Opt[Encoding]] with NodeCommand {
  val encoded = encoder("OBJECT", "ENCODING").key(key).result
  def decodeExpected = {
    case BulkStringMsg(string) => Opt(Encoding.byName(string.utf8String))
    case NullBulkStringMsg => Opt.Empty
  }
}

case class ObjectIdletime(key: ByteString) extends RedisOptLongCommand with NodeCommand {
  val encoded = encoder("OBJECT", "IDLETIME").key(key).result
}

case class Persist(key: ByteString) extends RedisBooleanCommand with NodeCommand {
  val encoded = encoder("PERSIST").key(key).result
}

case class Pexpire(key: ByteString, milliseconds: Long) extends RedisBooleanCommand with NodeCommand {
  val encoded = encoder("PEXPIRE").key(key).add(milliseconds).result
}

case class Pexpireat(key: ByteString, millisecondsTimestamp: Long) extends RedisBooleanCommand with NodeCommand {
  val encoded = encoder("PEXPIREAT").key(key).add(millisecondsTimestamp).result
}

case class Pttl(key: ByteString) extends RedisCommand[Opt[Opt[Long]]] with NodeCommand {
  val encoded = encoder("PTTL").key(key).result
  def decodeExpected = {
    case IntegerMsg(-2) => Opt.Empty
    case IntegerMsg(-1) => Opt(Opt.Empty)
    case IntegerMsg(ttl) => Opt(Opt(ttl))
  }
}

case object Randomkey extends RedisOptBinaryCommand with NodeCommand {
  val encoded = encoder("RANDOMKEY").result
}

case class Rename(key: ByteString, newkey: ByteString) extends RedisUnitCommand with NodeCommand {
  val encoded = encoder("RENAME").key(key).key(newkey).result
}

case class Renamenx(key: ByteString, newkey: ByteString) extends RedisBooleanCommand with NodeCommand {
  val encoded = encoder("RENAMENX").key(key).key(newkey).result
}

case class Restore(key: ByteString, ttl: Long, serializedValue: ByteString, replace: Boolean)
  extends RedisUnitCommand with NodeCommand {
  val encoded = encoder("RESTORE").key(key).add(ttl).add(serializedValue).addFlag("REPLACE", replace).result
}

case class Scan(cursor: Cursor, matchPattern: Opt[ByteString], count: Opt[Long])
  extends RedisCommand[(Cursor, Seq[ByteString])] with NodeCommand {
  val encoded = encoder("SCAN").add(cursor.raw).optAdd("MATCH", matchPattern).optAdd("COUNT", count).result
  def decodeExpected = {
    case ArrayMsg(IndexedSeq(BulkStringMsg(cursorString), ArrayMsg(elements))) =>
      (Cursor(cursorString.utf8String.toLong), elements.map {
        case BulkStringMsg(bs) => bs
        case msg => throw new UnexpectedReplyException(s"Expected multi bulk reply, but one of the elements is $msg")
      })
  }
}

sealed abstract case class AbstractSort[T](key: ByteString, by: Opt[SortPattern], limit: Opt[SortLimit],
  gets: Seq[SortPattern], asc: Boolean, alpha: Boolean, destination: Opt[ByteString]) extends RedisCommand[T] with NodeCommand {
  val encoded = {
    val enc = encoder("SORT").key(key).optAdd("BY", by).optAdd("LIMIT", limit)
    gets.foreach(sp => enc.add("GET").add(sp))
    enc.addFlag("DESC", !asc).addFlag("ALPHA", alpha).optKey("STORE", destination).result
  }
}

class Sort(key: ByteString, by: Opt[SortPattern], limit: Opt[SortLimit], asc: Boolean, alpha: Boolean)
  extends AbstractSort[Seq[ByteString]](key, by, limit, Nil, asc, alpha, Opt.Empty) with RedisBinarySeqCommand
object Sort {
  def apply(key: ByteString, by: Opt[SortPattern] = Opt.Empty, limit: Opt[SortLimit] = Opt.Empty, asc: Boolean, alpha: Boolean) =
    new Sort(key, by, limit, asc, alpha)
}

class SortGet(key: ByteString, gets: Seq[SortPattern], by: Opt[SortPattern], limit: Opt[SortLimit], asc: Boolean, alpha: Boolean)
  extends AbstractSort[Seq[Seq[Opt[ByteString]]]](key, by, limit, gets, asc, alpha, Opt.Empty) {

  def decodeExpected = {
    case ArrayMsg(elements) =>
      val valuesPerKey = gets.size min 1
      val it = elements.iterator.map {
        case NullBulkStringMsg => Opt.Empty
        case BulkStringMsg(bytes) => Opt(bytes)
        case msg => throw new UnexpectedReplyException(s"Expected multi bulk reply but one of the elements is $msg")
      }.grouped(valuesPerKey)
      it.to[ArrayBuffer]
  }
}
object SortGet {
  def apply(key: ByteString, gets: Seq[SortPattern], by: Opt[SortPattern], limit: Opt[SortLimit], asc: Boolean, alpha: Boolean) =
    new SortGet(key, gets, by, limit, asc, alpha)
}

class SortStore(key: ByteString, destination: ByteString, by: Opt[SortPattern], limit: Opt[SortLimit], gets: Seq[SortPattern], asc: Boolean, alpha: Boolean)
  extends AbstractSort[Long](key, by, limit, gets, asc, alpha, Opt(destination)) with RedisLongCommand

object SortStore {
  def apply(key: ByteString, destination: ByteString, by: Opt[SortPattern], limit: Opt[SortLimit], gets: Seq[SortPattern], asc: Boolean, alpha: Boolean) =
    new SortStore(key, destination, by, limit, gets, asc, alpha)
}

case class Ttl(key: ByteString) extends RedisCommand[Opt[Opt[Long]]] with NodeCommand {
  val encoded = encoder("TTL").key(key).result
  def decodeExpected = {
    case IntegerMsg(-2) => Opt.Empty
    case IntegerMsg(-1) => Opt(Opt.Empty)
    case IntegerMsg(ttl) => Opt(Opt(ttl))
  }
}

case class Type(key: ByteString) extends RedisCommand[RedisType] with NodeCommand {
  val encoded = encoder("TYPE").key(key).result
  def decodeExpected = {
    case SimpleStringStr(str) => RedisType.byName(str)
  }
}

case class Wait(numslaves: Int, timeout: Long) extends RedisLongCommand with NodeCommand {
  val encoded = encoder("WAIT").add(numslaves).add(timeout).result
}

sealed abstract class Encoding(val name: String) extends NamedEnum
sealed trait StringEncoding extends Encoding
sealed trait ListEncoding extends Encoding
sealed trait SetEncoding extends Encoding
sealed trait HashEncoding extends Encoding
sealed trait SortedSetEncoding extends Encoding

object Encoding extends NamedEnumCompanion[Encoding] {
  case object Raw extends Encoding("raw") with StringEncoding
  case object Int extends Encoding("int") with StringEncoding
  case object ZipList extends Encoding("ziplist") with ListEncoding with HashEncoding with SortedSetEncoding
  case object LinkedList extends Encoding("linkedlist") with ListEncoding
  case object IntSet extends Encoding("intset") with SetEncoding
  case object HashTable extends Encoding("hashtable") with SetEncoding with HashEncoding
  case object SkipList extends Encoding("skiplist") with SortedSetEncoding
  case object EmbStr extends Encoding("embstr") with StringEncoding

  val values: List[Encoding] = caseObjects
}

case class Cursor(raw: Long) extends AnyVal {
  override def toString = raw.toString
}
object Cursor {
  val NoCursor = Cursor(0)
}

case class SortLimit(offset: Long, count: Long)
object SortLimit {
  implicit val SortLimitArg: CommandArg[SortLimit] =
    CommandArg((ce, sl) => ce.add(sl.offset).add(sl.count))
}

sealed abstract class SortPattern(val repr: ByteString)
case object SelfPattern extends SortPattern(ByteString("#"))
case class KeyPattern(pattern: ByteString) extends SortPattern(pattern)
case class HashFieldPattern(keyPattern: ByteString, fieldPattern: ByteString)
  extends SortPattern(keyPattern ++ ByteString("->") ++ fieldPattern)
object SortPattern {
  implicit val SortPatternArg: CommandArg[SortPattern] = CommandArg((ce, sp) => ce.add(sp.repr))
}

sealed abstract class RedisType(val name: String) extends NamedEnum
object RedisType extends NamedEnumCompanion[RedisType] {
  case object None extends RedisType("none")
  case object String extends RedisType("string")
  case object List extends RedisType("list")
  case object Set extends RedisType("set")
  case object Zset extends RedisType("zset")
  case object Hash extends RedisType("hash")

  val values: List[RedisType] = caseObjects
}
