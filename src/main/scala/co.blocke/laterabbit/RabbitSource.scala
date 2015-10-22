package co.blocke
package laterabbit

import Util.await
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import akka.actor._
import akka.stream.scaladsl._
import akka.stream.actor.ActorPublisher
import akka.pattern.ask
import akka.util.Timeout
import com.thenewmotion.akka.rabbitmq._
import com.rabbitmq.client._

case class RabbitActor[T](binding:Binding, chan:ActorRef)(implicit system:ActorSystem, marshaller: RabbitUnmarshaller[T]) extends ActorPublisher[QMessage[T]] {

	val queueName = binding match {
		case tb : LateTopic =>  // For topics we have to bind to a queue
			chan ! ChannelMessage{ _.queueBind(tb.queue.name,tb.exchange,tb.routingKey) }
			tb.queue.name
		case qb : LateQueue => qb.name
	}

	// Register ourselves please
	chan ! ChannelMessage{ ch => ch.basicConsume(queueName, false, new DefaultConsumer(ch) {
		override def handleDelivery(
			consumerTag : String,
			envelope    : Envelope,
			properites  : AMQP.BasicProperties,
			body        : Array[Byte]) = {
			self ! new QMessage(envelope.getDeliveryTag(), marshaller.unmarshall(body,None,Some("UTF-8")), chan, envelope.isRedeliver)
		}
	})}

	override def receive = {
		case msg:QMessage[T] =>
			if(isActive && totalDemand > 0) 
				onNext(msg)
			else
				msg.nack()
	}

	override def postStop() {
		system.stop(chan)
	}
}

object RabbitSource {
	def apply[T](
		rabbitControl : ActorRef,
		binding       : Binding,
		channelQOS    : Int = 1
	)(implicit system:ActorSystem, marshaller: RabbitUnmarshaller[T]) = {
		// Declare bound queue
		binding match {
			case tb : LateTopic => rabbitControl ! tb.queue
			case qb : LateQueue => rabbitControl ! qb
		}

		// Get a new channel
		implicit val timeout:Timeout = 5.seconds

		val conn = await ( (rabbitControl ? GetConnection()) ).asInstanceOf[ActorRef]
		val chan = await ( conn ? CreateChannel(ChannelActor.props()) )
		chan match {
			case ChannelCreated(ref) => 
				// Set qos on channel
				ref ! ChannelMessage { _.basicQos(channelQOS) }

				// returns akka.streams.Source
				Source.actorPublisher[QMessage[T]](Props( new RabbitActor[T](binding, ref) ))
			case _ => 
				throw new Exception("Can't create RabbitSource")
		}
	}
}

// case class BodyAs[T](){
// 	def show()(implicit unmarshaller:(Array[Byte]=>T), body:Array[Byte]) {	
// 		println( unmarshaller(body) )
// 	}
// }