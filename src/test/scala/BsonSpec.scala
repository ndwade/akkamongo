package iteratees

import akka.actor.IO
import akka.actor.IO.{ Chunk, EOF, IterateeRef }
import akka.util.ByteString

import org.specs2._

class BsonSpec extends mutable.Specification {
  import BsonIteratees._

  "The Bson Iteratees" should {
    "read an int32" in {
      val input = Chunk(ByteString(58,0,0,0))
      val (iteratee, restOfInput) = readInt32(input)
      iteratee.get mustEqual(58)
      restOfInput  mustEqual(Chunk.empty)
    }

    "read several int32 values" in {
      // An IterateeRef provides an implementation of map, flatMap and
      // apply.  These are handy so you can compose iteratees to do a job,
      // and to nicely pull out the result (e.g., in a for comprehension).
      // The IterateeRefSync is an implementation of the trait that also
      // manages the intermediate state (Iteratee[A], Input), and will
      // thread that through the args to map/flatMap .  Kick off the whole
      // thing by throwing some Input at it via apply().
      val state = IO.IterateeRef.sync()
      state flatMap(_ =>
        for {
          int1 <- readInt32
          int2 <- readInt32
        } yield {
          int1 mustEqual(58)
          int2 mustEqual(59)
        })

      // This is what triggers running of the iteratee by passing Input to
      // the iteratee, we get the parsing to work.
      state(IO Chunk ByteString(58,0,0,0,  59,0,0,0))
    }

    "read a cstring" in {
      val state = IO.IterateeRef.sync()
      state flatMap(_ =>
        for {
          string <- readCString
        } yield {
          string mustEqual("admin.$cmd")
        })

      // This is what triggers running of the iteratee by passing Input to
      // the iteratee, we get the parsing to work.
      state(IO Chunk ByteString(97,100,109,105,110,46,36,99,109,100, 0))
    }

    "read a cstring followed by more data" in {
      val state = IO.IterateeRef.sync()
      state flatMap(_ =>
        for {
          string <- readCString
          dalmations <- readInt32
        } yield {
          string     mustEqual("admin.$cmd")
          dalmations mustEqual(101)
        })

      // This is what triggers running of the iteratee by passing Input to
      // the iteratee, we get the parsing to work.
      state(IO Chunk ByteString(97,100,109,105,110,46,36,99,109,100,0,    101, 0, 0, 0))
    }

    "readInt32Element should work" in {
      val state = IO.IterateeRef.sync()
      state flatMap(_ =>
        for {
          elType  <- IO take 1  // consume the elType for this test
          element <- readInt32Element
        } yield {
          element.value mustEqual(1)
          element.name  mustEqual("ismaster")
        })

      // name is "ismaster", value is 1
      state(IO Chunk ByteString(16, 105,115,109,97,115,116,101,114,0,  1,0,0,0, 255,255,255,255))
    }

    "read an element" in {
      val state = IO.IterateeRef.sync()
      state flatMap(_ =>
        for {
          element <- readElement
        } yield {
//          println("======== element: " + element)
//          println("======== element: " + element(EOF(None)))
          element match {
            case anInt: BsonInt32 => {
              println("====== BsonInt32")
              anInt.value mustEqual(1)
              anInt.name mustEqual("ismaster")
            }
            case x => {
              println("====== FAILURE: " + x)
              failure("should be a BsonInt32 but was: " + x)
            }
          }
        })

      // Type 16: an int, name is "ismaster", value is 1
      state(IO Chunk ByteString(16,  105,115,109,97,115,116,101,114,0,  1,0,0,0, 255,255,255,255))
    }

  }
}
