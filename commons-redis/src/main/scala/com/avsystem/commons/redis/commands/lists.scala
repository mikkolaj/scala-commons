package com.avsystem.commons
package redis.commands

import com.avsystem.commons.misc.Opt
import com.avsystem.commons.redis._
import com.avsystem.commons.redis.commands.ReplyDecoders._

trait ListsApi extends ApiSubset {
  def lindex(key: Key, index: Long): Result[Opt[Value]] =
    execute(new Lindex(key, index))
  def linsert(key: Key, pivot: Value, value: Value, before: Boolean = false): Result[Opt[Long]] =
    execute(new Linsert(key, before, pivot, value))
  def llen(key: Key): Result[Long] =
    execute(new Llen(key))
  def lpop(key: Key): Result[Opt[Value]] =
    execute(new Lpop(key))
  def lpush(key: Key, value: Value, values: Value*): Result[Long] =
    execute(new Lpush(key, value +:: values))
  def lpush(key: Key, values: Iterable[Value]): Result[Long] =
    execute(new Lpush(key, values))
  def lpushx(key: Key, value: Value): Result[Long] =
    execute(new Lpushx(key, value))
  def lrange(key: Key, start: Long = 0, stop: Long = -1): Result[Seq[Value]] =
    execute(new Lrange(key, start, stop))
  def lrem(key: Key, value: Value, count: RemCount = RemCount.All): Result[Long] =
    execute(new Lrem(key, count, value))
  def lset(key: Key, index: Long, value: Value): Result[Unit] =
    execute(new Lset(key, index, value))
  def ltrim(key: Key, start: Long = 0, stop: Long = -1): Result[Unit] =
    execute(new Ltrim(key, start, stop))
  def rpop(key: Key): Result[Opt[Value]] =
    execute(new Rpop(key))
  def rpoplpush(source: Key, destination: Key): Result[Opt[Value]] =
    execute(new Rpoplpush(source, destination))
  def rpush(key: Key, value: Value, values: Value*): Result[Long] =
    execute(new Rpush(key, value +:: values))
  def rpush(key: Key, values: Iterable[Value]): Result[Long] =
    execute(new Rpush(key, values))
  def rpushx(key: Key, value: Value): Result[Long] =
    execute(new Rpushx(key, value))

  private final class Lindex(key: Key, index: Long) extends RedisOptDataCommand[Value] with NodeCommand {
    val encoded = encoder("LINDEX").key(key).add(index).result
  }

  private final class Linsert(key: Key, before: Boolean, pivot: Value, value: Value)
    extends RedisPositiveLongCommand with NodeCommand {
    val encoded = encoder("LINSERT").key(key).add(if (before) "BEFORE" else "AFTER").data(pivot).data(value).result
  }

  private final class Llen(key: Key) extends RedisLongCommand with NodeCommand {
    val encoded = encoder("LLEN").key(key).result
  }

  private final class Lpop(key: Key) extends RedisOptDataCommand[Value] with NodeCommand {
    val encoded = encoder("LPOP").key(key).result
  }

  private final class Lpush(key: Key, values: Iterable[Value]) extends RedisLongCommand with NodeCommand {
    requireNonEmpty(values, "values")
    val encoded = encoder("LPUSH").key(key).datas(values).result
  }

  private final class Lpushx(key: Key, value: Value) extends RedisLongCommand with NodeCommand {
    val encoded = encoder("LPUSHX").key(key).data(value).result
  }

  private final class Lrange(key: Key, start: Long, stop: Long)
    extends RedisDataSeqCommand[Value] with NodeCommand {
    val encoded = encoder("LRANGE").key(key).add(start).add(stop).result
  }

  private final class Lrem(key: Key, count: RemCount, value: Value) extends RedisLongCommand with NodeCommand {
    val encoded = encoder("LREM").key(key).add(count.raw).data(value).result
  }

  private final class Lset(key: Key, index: Long, value: Value) extends RedisUnitCommand with NodeCommand {
    val encoded = encoder("LSET").key(key).add(index).data(value).result
  }

  private final class Ltrim(key: Key, start: Long, stop: Long) extends RedisUnitCommand with NodeCommand {
    val encoded = encoder("LTRIM").key(key).add(start).add(stop).result
  }

  private final class Rpop(key: Key) extends RedisOptDataCommand[Value] with NodeCommand {
    val encoded = encoder("RPOP").key(key).result
  }

  private final class Rpoplpush(source: Key, destination: Key) extends RedisOptDataCommand[Value] with NodeCommand {
    val encoded = encoder("RPOPLPUSH").key(source).key(destination).result
  }

  private final class Rpush(key: Key, values: Iterable[Value]) extends RedisLongCommand with NodeCommand {
    requireNonEmpty(values, "values")
    val encoded = encoder("RPUSH").key(key).datas(values).result
  }

  private final class Rpushx(key: Key, value: Value) extends RedisLongCommand with NodeCommand {
    val encoded = encoder("RPUSHX").key(key).data(value).result
  }
}

trait BlockingListsApi extends ApiSubset {
  def blpop(key: Key): Result[Value] =
    execute(new Blpop(key.single, 0).map(_.get._2))
  def blpop(key: Key, keys: Key*): Result[(Key, Value)] =
    execute(new Blpop(key +:: keys, 0).map(_.get))
  def blpop(keys: Iterable[Key]): Result[(Key, Value)] =
    execute(new Blpop(keys, 0).map(_.get))
  def blpop(key: Key, timeout: Int): Result[Opt[Value]] =
    execute(new Blpop(key.single, timeout).map(_.map(_._2)))
  def blpop(keys: Iterable[Key], timeout: Int): Result[Opt[(Key, Value)]] =
    execute(new Blpop(keys, timeout))
  def brpop(key: Key): Result[Value] =
    execute(new Brpop(key.single, 0).map(_.get._2))
  def brpop(key: Key, keys: Key*): Result[(Key, Value)] =
    execute(new Brpop(key +:: keys, 0).map(_.get))
  def brpop(keys: Iterable[Key]): Result[(Key, Value)] =
    execute(new Brpop(keys, 0).map(_.get))
  def brpop(key: Key, timeout: Int): Result[Opt[Value]] =
    execute(new Brpop(key.single, timeout).map(_.map(_._2)))
  def brpop(keys: Iterable[Key], timeout: Int): Result[Opt[(Key, Value)]] =
    execute(new Brpop(keys, timeout))
  def brpoplpush(source: Key, destination: Key): Result[Value] =
    execute(new Brpoplpush(source, destination, 0).map(_.get))
  def brpoplpush(source: Key, destination: Key, timeout: Int): Result[Opt[Value]] =
    execute(new Brpoplpush(source, destination, timeout))

  private final class Blpop(keys: Iterable[Key], timeout: Int)
    extends AbstractRedisCommand[Opt[(Key, Value)]](nullMultiBulkOr(multiBulkPair(bulk[Key], bulk[Value]))) with ConnectionCommand {
    val encoded = encoder("BLPOP").keys(keys).add(timeout).result
  }

  private final class Brpop(keys: Iterable[Key], timeout: Int)
    extends AbstractRedisCommand[Opt[(Key, Value)]](nullMultiBulkOr(multiBulkPair(bulk[Key], bulk[Value]))) with ConnectionCommand {
    val encoded = encoder("BRPOP").keys(keys).add(timeout).result
  }

  private final class Brpoplpush(source: Key, destination: Key, timeout: Int)
    extends AbstractRedisCommand[Opt[Value]](nullMultiBulkOr(bulk[Value])) with ConnectionCommand {
    val encoded = encoder("BRPOPLPUSH").key(source).key(destination).add(timeout).result
  }
}

class RemCount private(val raw: Long) extends AnyVal
object RemCount {
  def apply(count: Long, fromHead: Boolean): RemCount = {
    require(count > 0, "Count must be positive")
    new RemCount(if (fromHead) count else -count)
  }
  final val All = new RemCount(0)
  def fromHead(count: Long) = RemCount(count, fromHead = true)
  def fromTail(count: Long) = RemCount(count, fromHead = false)
}
