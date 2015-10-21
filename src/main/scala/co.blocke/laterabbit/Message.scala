package co.blocke
package laterabbit

/**
  This is the command object sent to RabbitControl for message publishing to queue. 
  Not to be confused with QMessage, which is a wrapper for stuff pulled off the queue--used to hold ack-ing info.
  */
import com.thenewmotion.akka.rabbitmq._
import com.rabbitmq.client.Channel

trait Publisher {
	val exchangeName: String
	val routingKey: String
	def apply(c: Channel, data: Array[Byte], properties: BasicProperties): Unit
}

private [laterabbit] class PublisherImpl(val exchangeName: String, val routingKey: String) extends Publisher {
  def apply(c: Channel, data: Array[Byte], properties: BasicProperties) = 
    c.basicPublish(exchangeName, routingKey, properties, data)
}

object Publisher {
	def queue(name:String) = new PublisherImpl("", name)
	def exchange(exchange:String, routingKey:String) = new PublisherImpl(exchange,routingKey)
}

object Message {
	def apply[T](body: T, publisher: Publisher)(implicit marshaller: RabbitMarshaller[T]) = 
		ChannelMessage ({ ch => publisher.apply(ch,marshaller.marshall(body),null) }, dropIfNoChannel = false)

	def topic[T](
		message: T, 
		routingKey: String, 
		exchange: String = RabbitControl.topicExchangeName)(implicit marshaller: RabbitMarshaller[T]) = 
		apply(message, Publisher.exchange(exchange, routingKey))

	def queue[T](
		message: T,
		queue: String)(implicit marshaller: RabbitMarshaller[T]) = 
		apply(message, Publisher.queue(queue))
}