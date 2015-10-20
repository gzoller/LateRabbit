package co.blocke
package laterabbit

import akka.actor.ActorRef
import com.thenewmotion.akka.rabbitmq.ChannelMessage

case class QMessage[T](val deliveryTag: Long, val body: T, chan:ActorRef) {
	def ack() : Unit = chan ! ChannelMessage { _.basicAck(deliveryTag, false) }
	def nack(): Unit = chan ! ChannelMessage { _.basicNack(deliveryTag, false, true) }

	def map[U](f: (T) => U): QMessage[U] = QMessage[U](this.deliveryTag, f(this.body), this.chan)
}