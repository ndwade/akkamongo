package iteratees

import akka.actor.IO
import akka.util.ByteString

import akka.actor.IO.{ Iteratee }
import java.nio.ByteBuffer

// A lot of ideas from
// https://github.com/derekjw/fyrie-redis/blob/master/src/main/scala/net/fyrie/redis/protocol/Iteratees.scala

case class BsonProtocolException(message: String) extends RuntimeException(message)

// Since we have to have constants for matching against ByteStrings anyway,
// instead of defining a parallel enumeration or sealed trait/case objects,
// just make these look like a type enum (of sorts)

//case class Document(length: Int, bytes: ByteString)
case class Document(length: Int, es: List[BsonElement])
sealed trait BsonElement { 
  def toByteString: ByteString = throw new UnsupportedOperationException
  def write(bb: ByteBuffer): Unit = throw new UnsupportedOperationException
}
case class BsonDouble(name: String, value: Double) extends BsonElement
case class BsonString(name: String, value: String) extends BsonElement
case class BsonDocument(name: String, value: Document) extends BsonElement
case class BsonArray(name: String, value: Document) extends BsonElement // Keys for an array are integers
case class BsonBinary(name: String, value: ByteString) extends BsonElement
case class BsonUndefined(name: String) extends BsonElement
case class BsonObjectId(name: String, value: ByteString) extends BsonElement
case class BsonBoolean(name: String, value: Boolean) extends BsonElement
//case class BsonDatetime(name: String, value: Long) extends BsonElement
case class BsonNull(name: String) extends BsonElement
//case class BsonRegex(name: String, value: Regex) extends BsonElement
//case class BsonDbPointer(name: String, value: ByteString) extends BsonElement
case class BsonJsCode(name: String, value: String) extends BsonElement
case class BsonSymbol(name: String, value: Symbol) extends BsonElement
case class BsonJsCodeWithScope(name: String, value: String, scope: BsonDocument) extends BsonElement
case class BsonInt32(name: String, value: Int) extends BsonElement
//case class BsonTimestampe(name: String, value: Long) extends BsonElement
case class BsonInt64(name: String, value: Long) extends BsonElement
case class BsonMaxKey(name: String) extends BsonElement
case class BsonMinKey(name: String) extends BsonElement



object BsonTypes {
  type BsonType = ByteString
  // These are the values for the single byte that is used to distinguish
  // bson types in the bson element type field
  final val DOUBLE       = ByteString(1)
  final val STRING       = ByteString(2)
  final val DOCUMENT     = ByteString(3)
  final val ARRAY        = ByteString(4)
  final val BINARY       = ByteString(5)
  final val UNDEFINED    = ByteString(6) // Deprecated
  final val OBJECT_ID    = ByteString(7)
  final val BOOLEAN      = ByteString(8)
  final val UTC_DATETIME = ByteString(9)
  final val NULL         = ByteString(10)
  final val REGEX        = ByteString(11)
  final val DB_POINTER   = ByteString(12) // Deprecated
  final val JS_CODE      = ByteString(13)
  final val SYMBOL       = ByteString(14)
  final val JS_CODE_W_S  = ByteString(15)
  final val INT32        = ByteString(16)
  final val TIMESTAMP    = ByteString(17)
  final val INT64        = ByteString(18)
  final val MAX_KEY      = ByteString(127)
  final val MIN_KEY      = ByteString(255)
}

object BsonIteratees {
  import BsonTypes._
  implicit val byteOrder = java.nio.ByteOrder.LITTLE_ENDIAN // TODO:

  final val ZERO   = ByteString(0)

  final val readUntilZero = IO takeUntil ZERO

  // TODO: Why doesn't my implicit byteOrder work?...because I'm down in Javaland?
  final val bytesToInt32  = (bytes: ByteString) => bytes.toByteBuffer.order(byteOrder).getInt
  final val bytesToInt64  = (bytes: ByteString) => bytes.toByteBuffer.order(byteOrder).getLong
  final val bytesToDouble = (bytes: ByteString) => bytes.toByteBuffer.order(byteOrder).getDouble
  final val bytesToString = (bytes: ByteString) => bytes.utf8String

  // Pull four bytes off the wire and convert to int32
  final val readInt32: Iteratee[Int] = (IO take 4) map bytesToInt32
  final val readInt64   = (IO take 8) map bytesToInt64
  final val readDouble  = (IO take 8) map bytesToDouble
  final val readCString = readUntilZero map bytesToString
  final val readName    = readCString

  // 16 "somename" 1, 0, 0, 0
  final val readInt32Element: Iteratee[BsonInt32] = for {
    name <- readName
    value <- readInt32
  } yield BsonInt32(name, value)
  
  // btw - all vals in objects are final by default
  val readStringElement = for {
    name <- readName
    value <- readCString
  } yield BsonString(name, value)


  final val readElement/*: Iteratee[BsonElement] */ = for {
    elType <- IO take 1
    element <- iterOf(elType)
  } yield element
  
  def iterOf(element: ByteString) = element match {
    case INT32 => readInt32Element
    case STRING => readStringElement
    case DOCUMENT => readBsonDocument
  }
  
  val readElementList = {
    def step(es: List[BsonElement]): Iteratee[List[BsonElement]] = {
      IO peek 1 flatMap {
        case ZERO => IO take 1 flatMap { _ => IO.Done(es.reverse) }
        case _ => readElement flatMap { e => step(e :: es) }
      }
    }
    step(Nil)
  }
  
  // see what happens when you omit the return type annotation...
  val readBsonDocument: Iteratee[BsonDocument] = for {
    name <- readName
    value <- readDocument
  } yield BsonDocument(name, value)
  

  val readDocument = for {
    docLength <- readInt32
    es <- readElementList
  } yield Document(docLength, es)

}
