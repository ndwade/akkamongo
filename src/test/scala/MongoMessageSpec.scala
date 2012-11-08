package iteratees

import akka.actor.IO
import akka.actor.IO.{ Chunk, IterateeRef }
import akka.util.ByteString

import org.specs2._

class MongoMessageSpec extends mutable.Specification {
  import MongoIteratees._

  // Read off the wire from a mongo client
  val anOpQuery = ByteString(
     // The header
     58, 0, 0, 0,
      1, 0, 0, 0,
      0, 0, 0, 0,
    -44, 7, 0, 0,

     // The OpQuery message
     0, 0, 0, 0,                                     // flags
    97, 100, 109, 105, 110, 46, 36, 99, 109, 100, 0, // full collection name
    0, 0, 0, 0,                                      // number to skip
    -1, -1, -1, -1,                                  // number to return

    // The query document
    19, 0, 0, 0,                               // document length
    16,                                        //  type is 32 bit integer
    105, 115, 109, 97, 115, 116, 101, 114, 0,  // name is "admin.$cmd"
    1, 0, 0, 0,                                // value is 1
    0)                                         // end of document

  "The MongoMessage Iteratees" should {
    "Read a Mongo Message Header" in {
      val state = IO.IterateeRef.sync()
      state flatMap(_ =>
        for (header <- readHeader) yield {
          header.msgLength  mustEqual(58)
          header.requestId  mustEqual(1)
          header.responseTo mustEqual(0)
          header.opCode     mustEqual(2004)
        })

      // This is what triggers running of the iteratee by passing Input to
      // the iteratee, we get the parsing to work.
      state(IO Chunk anOpQuery)
    }

    "Read a Mongo OpQuery Message" in {
      val state = IO.IterateeRef.sync()
      state flatMap(_ =>
        for {
          header <- readHeader
          opQuery <- readMessage(header)
        } yield {
          println("============ " + opQuery)
          opQuery.flags mustEqual(0)
          opQuery.fullCollectionName  mustEqual("admin.$cmd")
          opQuery.numberToSkip  mustEqual(0)
          opQuery.numberToReturn  mustEqual(-1)
          //opQuery.query  mustEqual(0)
          opQuery.returnFieldSelector  mustEqual(None)
        })

      // This is what triggers running of the iteratee by passing Input to
      // the iteratee, we get the parsing to work.
      state(IO Chunk anOpQuery)
    }
  }
}
