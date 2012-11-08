package iteratees

import akka.actor.{ Actor, ActorLogging, ActorSystem, IOManager, Props }
import akka.actor.{ IO }
import akka.actor.IO.{ Handle, IterateeRef }

import akka.util.{ ByteString, Duration }

import akka.dispatch.{ Await, Promise }

import java.net.InetSocketAddress

/**
 * V2: Server and ClientHandler
 * 1. Server listens on a well know socket
 * 2. Server accepts clients and hands client socket to ClientHandler
 * 3. ClienthHandler handles all activity for one client (maintains client state)
 *
 * This is based off of the SimpleEchoServer in
 * https://github.com/akka/akka/blob/master/akka-actor-tests/src/test/scala/akka/actor/IOActor.scala
 */
class Server(port: Int) extends Actor with ActorLogging {

  val server = IOManager(context.system) listen new InetSocketAddress(port)

  def receive = {

    case IO.NewClient(server) =>
      log.debug("New Client")
      // We first need to create our client handler, since we must tell the
      // IO Manager which actor should handle messages for the socket.  We
      // will pass it a Promise of a socket, since we don't have it yet,
      // and then send the socket when we get it.
      val socketPromise = Promise[IO.SocketHandle]()(context.dispatcher)
      val ch = context.actorOf(Props(new ClientHandler(socketPromise)))

      // Get the client socket and tell the IO Manager what actor should
      // receive messages related to the socket (IO.Read, etc).
      val socket = server.accept()(ch)
      socketPromise success socket     // pass socket to ClientHandler

    case unknown @ _ =>
      log.error("!!! Server Doesn't understand: " + unknown)
  }

  override def postStop {
    server.close  // Shutdown our own socket
  }
}

class ClientHandler(socketPromise: Promise[IO.SocketHandle]) extends Actor with ActorLogging {

  import MongoIteratees._

  val socket = Await.result(socketPromise.future, Duration(1, "second"))
  val state = IO.IterateeRef.async()(context.dispatcher)
  state flatMap (_ => IO repeat processRequest)

  // Some per-client state to manage
  var clientState = 0

  def receive = {
    case IO.Read(`socket`, bytes) =>
      log.debug("IO.Read: " + bytes.toString)
      clientState += 1
      state(IO Chunk bytes)

    // The client closed the socket, so we can now exit
    case IO.Closed(`socket`, cause) =>
      log.debug("IO.Closed: " + socket + " cause: " + cause + " Stopping!")
      context.stop(self)

    case msg: MongoMessage =>
        log.debug("GOT MongoMessage: " + msg)

    case unknown @ _ =>
      log.error("!!! ClientHandler Doesn't understand: " + unknown)
  }

  override def postStop {
    socket.close()
  }

  // This is what the iteratees will hook into.  We simply repeatedly read
  // a bunch of bytes and send a message to ourself.  The sending of the
  // message isn't necessary if we have a IO.IterateeRef.sync(), but is we
  // have an async version, then it is necessary?
  def processRequest =
    for {
      msgHeader <- readHeader
      msg       <- readMessage(msgHeader)
    } yield {
      self ! msg
    }
}

object Main extends App {
  val port = Option(System.getenv("PORT")) map (_.toInt) getOrElse 8899
  println("== Main: Starting Server on port " + port)
  val system = ActorSystem()
  val server = system.actorOf(Props(new Server(port)))
  println("== Main: exiting")
}
