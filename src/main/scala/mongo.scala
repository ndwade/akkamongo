package iteratees

import akka.actor.IO
import akka.util.ByteString



case class MongoHeader(msgLength: Int, requestId: Int, responseTo: Int, opCode: Int) {
  /**
   * Return the number of bytes in the body of the message
   */
  def bodyLength = msgLength - 16
}

sealed trait MongoMessage
case class FakeMessage(header: MongoHeader, bytes: ByteString) extends MongoMessage
case class OpQuery(header: MongoHeader,
                   flags: Int,
                   fullCollectionName: String,
                   numberToSkip: Int,
                   numberToReturn: Int,
                   query: Document,
                   returnFieldSelector: Option[Document]) extends MongoMessage

object MongoMessage {
  def parseMessage(bytes: ByteString, header: MongoHeader) = header.opCode match {
    case 2004 => parseOpQuery(bytes)
    case _ => throw new RuntimeException("Do not know how to parse: " + header)
  }

  private def parseOpQuery(bytes: ByteString) = {
    
  }
}

object MongoIteratees {
  import BsonIteratees._

  final val readHeader = for {
    msgLength  <- readInt32
    requestId  <- readInt32
    responseTo <- readInt32
    opCode     <- readInt32
  } yield MongoHeader(msgLength, requestId, responseTo, opCode)

//  final val readBody = (header: MongoHeader) => IO take header.bodyLength
  final val readMessage = (header: MongoHeader) => header.opCode match {
    case 2004 => readOpQuery(header)
    case _ => throw new RuntimeException("Don't understand Mongo Message type: " + header)
  }

  final val readOpQuery = (header: MongoHeader) =>
    for {
      flags              <- readInt32
      fullCollectionName <- readCString
      numberToSkip       <- readInt32
      numberToReturn     <- readInt32
      query <- readDocument 
      //query = Document()
      // TODO: How to test for EOF and optionally read another document
      // Could just repeatedly read documents and then figure out how many I found?
      //returnFieldSelector <- readOptionalDocument  TODO
      returnFieldSelector = None: Option[Document]
    } yield OpQuery(header, flags, fullCollectionName, numberToSkip, numberToReturn, query, returnFieldSelector)
}
