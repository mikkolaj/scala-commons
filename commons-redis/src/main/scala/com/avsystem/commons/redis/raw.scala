package com.avsystem.commons
package redis

import com.avsystem.commons.misc.Opt
import com.avsystem.commons.redis.RawCommand.Level
import com.avsystem.commons.redis.exception.ForbiddenCommandException
import com.avsystem.commons.redis.protocol.{ArrayMsg, BulkStringMsg, RedisMsg, RedisReply}

import scala.collection.mutable.ArrayBuffer

/**
  * One or more raw Redis commands. More lightweight than regular Scala collection
  * (avoids wrapping in case of single element).
  */
trait RawCommands {
  def emitCommands(consumer: RawCommand => Unit): Unit
}

trait RawCommand extends RawCommandPack with RawCommands with ReplyPreprocessor {
  def encoded: ArrayMsg[BulkStringMsg]
  def updateWatchState(message: RedisMsg, state: WatchState): Unit = ()
  def level: Level

  def checkLevel(minAllowed: Level, clientType: String) =
    if (!minAllowed.allows(level)) {
      throw new ForbiddenCommandException(this, clientType)
    }

  def rawCommands(inTransaction: Boolean) = this
  def emitCommands(consumer: RawCommand => Unit) = consumer(this)
  def createPreprocessor(replyCount: Int) = this
  def preprocess(message: RedisMsg, state: WatchState) = {
    updateWatchState(message, state)
    Opt(message)
  }

  protected def encoder(commandName: String*): CommandEncoder = {
    val res = new CommandEncoder(new ArrayBuffer)
    res.add(commandName)
    res
  }
}

trait UnsafeCommand extends RawCommand {
  def level = Level.Unsafe
}
trait ConnectionCommand extends RawCommand {
  def level = Level.Connection
}
trait OperationCommand extends RawCommand {
  def level = Level.Operation
}
trait NodeCommand extends RawCommand {
  def level = Level.Node
}

object RawCommand {
  case class Level(raw: Int) extends AnyVal {
    def allows(other: Level) = raw <= other.raw
  }
  object Level {
    val Unsafe = Level(0)
    val Connection = Level(1)
    val Operation = Level(2)
    val Node = Level(3)
  }
}

/**
  * One or more [[RawCommandPack]]s. More lightweight than regular Scala collection
  * (avoids wrapping in case of single element).
  */
trait RawCommandPacks {
  def emitCommandPacks(consumer: RawCommandPack => Unit): Unit
  def single: Opt[RawCommandPack] = Opt.Empty

  def requireLevel(minAllowed: Level, clientType: String): this.type = {
    emitCommandPacks(_.checkLevel(minAllowed, clientType))
    this
  }
}

/**
  * Represents a sequence of commands that is always executed atomically, using a single network call
  * on a single Redis connection.
  */
trait RawCommandPack extends RawCommandPacks {
  def rawCommands(inTransaction: Boolean): RawCommands
  def createPreprocessor(replyCount: Int): ReplyPreprocessor
  def checkLevel(minAllowed: Level, clientType: String): Unit

  def emitCommandPacks(consumer: RawCommandPack => Unit) = consumer(this)
  override def single: Opt[RawCommandPack] = this.opt
}

final class WatchState {
  var watching: Boolean = false
}

/**
  * Something that translates incoming [[com.avsystem.commons.redis.protocol.RedisMsg RedisMsg]]
  * messages and emits a single [[RedisReply]].
  * For example, it may handle transactions by extracting actual responses for every command from
  * the `EXEC` response and returning them in an [[ArrayMsg]] (see [[Transaction]]).
  */
trait ReplyPreprocessor {
  def preprocess(message: RedisMsg, watchState: WatchState): Opt[RedisReply]
}
