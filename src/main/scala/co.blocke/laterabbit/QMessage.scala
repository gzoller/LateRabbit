package co.blocke
package laterabbit

import akka.actor.ActorRef
import com.thenewmotion.akka.rabbitmq.ChannelMessage

case class QMessage[T](val deliveryTag: Long, val body: T, chan:ActorRef) {
	def ack() = {
		chan ! ChannelMessage { _.basicAck(deliveryTag, false) }
		body // We return unwrapped body here because after ack/nack the QMessage wrapper is no longer useful.
	}
	def nack() = {
		chan ! ChannelMessage { _.basicNack(deliveryTag, false, true) }
		body
	}

	def map[U](f: (T) => U): QMessage[U] = QMessage[U](this.deliveryTag, f(this.body), this.chan)
}