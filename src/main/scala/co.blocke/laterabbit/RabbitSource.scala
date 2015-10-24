package co.blocke
package laterabbit

import Util.await
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import akka.actor._
import akka.stream.scaladsl._
import akka.stream.actor._
import ActorPublisherMessage._
import akka.pattern.ask
import akka.util.Timeout

import com.thenewmotion.akka.rabbitmq._
import com.rabbitmq.client._


case class RabbitActor[T](queueName:String, chan:ActorRef)(implicit system:ActorSystem, marshaller: RabbitUnmarshaller[T]) extends ActorPublisher[QMessage[T]] {

	private val qBuf = scala.collection.mutable.Queue.empty[QMessage[T]]
	private val MAX_Q = 10
	private var stopping = false

	// Register ourselves please
	chan ! ChannelMessage{ ch => ch.basicConsume(queueName, false, new DefaultConsumer(ch) {
		override def handleDelivery(
			consumerTag : String,
			envelope    : Envelope,
			properites  : AMQP.BasicProperties,
			body        : Array[Byte]) = {
			// println("1) Sending a message from Rabbit")
			self ! new QMessage(envelope.getDeliveryTag(), marshaller.unmarshall(body,None,Some("UTF-8")), chan, envelope.isRedeliver)
		}
	})}

	def receive = {
		case Request(demand) =>
			// println("Flow is demanding work!")
			drain()
			if (stopping) tryStop()

		// A stream consumer detached
		case Cancel =>
			// println("Cancel flow")
			context stop self

		case msg:QMessage[T] =>
			// println("Got a message")
			qBuf.enqueue(msg)
			drain()
			// limitQosOnOverflow()
	}

	private def drain() = 
		while ((totalDemand > 0) && (qBuf.length > 0)) {
			// println("Consuming que buf message")
			onNext(qBuf.dequeue())	
		}

	private def tryStop(): Unit =
		if (qBuf.length == 0)
			onCompleteThenStop()

	// private def limitQosOnOverflow(): Unit = {
	// 	subscriptionActor.foreach { ref =>
	// 		// TODO - think this through
	// 		val desiredQos = if(qBuf.length > MAX_Q) 1 else presentQos
	// 		if (desiredQos == presentQos) subscriptionActor.foreach { ref =>
	// 			ref ! Subscription.SetQos(desiredQos)
	// 			presentQos = desiredQos
	// 		}
	// 	}
	// }

	override def postStop() {
		system.stop(chan)
	}
}

object RabbitSource {
	def apply[T](
		rabbitControl : ActorRef,
		queue         : LateQueue,
		channelQOS    : Int = 1
	)(implicit system:ActorSystem, marshaller: RabbitUnmarshaller[T]) = {
		// Declare bound queue
		rabbitControl ! queue

		// Get a new channel
		implicit val timeout:Timeout = 5.seconds

		val conn = await ( (rabbitControl ? GetConnection()) ).asInstanceOf[ActorRef]
		val chan = await ( conn ? CreateChannel(ChannelActor.props()) )
		chan match {
			case ChannelCreated(ref) => 
				// Set qos on channel
				ref ! ChannelMessage { _.basicQos(channelQOS) }

				// returns akka.streams.Source
				Source.actorPublisher[QMessage[T]](Props( new RabbitActor[T](queue.name, ref) ))
			case _ => 
				throw new Exception("Can't create RabbitSource")
		}
	}
}
