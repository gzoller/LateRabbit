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

/*
RabbitSource(
  rabbitControl,
  channel(qos = 3),
  consume(queue("such-queue", durable = true, exclusive = false, autoDelete = false)),
  body(as[Person]))

  def apply[L <: HList](
    rabbitControl: ActorRef,
    channelDirective: ChannelDirective,
    bindingDirective: BindingDirective,
    handler: Directive[L]
  )


		// case qos:QOS =>
		// 	publishChannel ! ChannelMessage { _.basicQos(qos.qos) }

	val in = Source.actorPublisher[RabbitMessage](Props( new RabbitConsumerActor(inChannel,inQueueName) ) )

*/

case class RabbitActor[T](binding:Binding, chan:ActorRef)(implicit marshaller: RabbitUnmarshaller[T]) extends ActorPublisher[QMessage[T]] {

	val queueName = binding match {
		case tb : DeclareTopic => tb.queue.name
		case qb : DeclareQueue => qb.name
	}

	// Register ourselves please
	chan ! ChannelMessage{ ch => ch.basicConsume(queueName, false, new DefaultConsumer(ch) {
		override def handleDelivery(
			consumerTag : String,
			envelope    : Envelope,
			properites  : AMQP.BasicProperties,
			body        : Array[Byte]) = {
			println("Inbound!")
			self ! new QMessage(envelope.getDeliveryTag(), marshaller.unmarshall(body,None,Some("UTF-8")), chan)
		}
	})}

	override def receive = {
		case msg:QMessage[T] =>
			println("Got a QMessage: "+msg)
			if(isActive && totalDemand > 0) 
				onNext(msg)
			else
				msg.nack()
	}
}

object RabbitSource {
	def apply[T](
		rabbitControl : ActorRef,
		binding       : Binding,
		bodyAs        : BodyAs[T],
		channelQOS    : Int = 1
	)(implicit marshaller: RabbitUnmarshaller[T]) = {
		// Declare bound queue
		binding match {
			case tb : DeclareTopic => rabbitControl ! tb.queue
			case qb : DeclareQueue => rabbitControl ! qb
		}

		// Get a new channel
		implicit val timeout:Timeout = 5.seconds
		// val chan = await( (rabbitControl ? GetConnection).map( _.asInstanceOf[ActorRef] ? CreateChannel(ChannelActor.props()) ) ).asInstanceOf[ChannelCreated].channel

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

// queueBind(java.lang.String queue, java.lang.String exchange, java.lang.String routingKey, java.util.Map<java.lang.String,java.lang.Object> arguments)

case class BodyAs[T](){
	def show()(implicit unmarshaller:(Array[Byte]=>T), body:Array[Byte]) {	
		println( unmarshaller(body) )
	}
}