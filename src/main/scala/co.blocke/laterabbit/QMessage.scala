package co.blocke
package laterabbit

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import akka.actor.ActorRef
import com.thenewmotion.akka.rabbitmq.ChannelMessage

trait MessageCB {
	def retry(p:List[String]):Unit
	def nack(p:List[String]):Unit
}

case class QMessage[T](
	deliveryTag: Long, 
	body: T, 
	chan:ActorRef, 
	isRedeliver:Boolean, 
	metaTags:List[String] = List.empty[String],
	msgCB:Option[MessageCB] = None
	){

	def ack() = {
		if( deliveryTag > 0L ) chan ! ChannelMessage { _.basicAck(deliveryTag, false) }
		body // We return unwrapped body here because after ack/nack the QMessage wrapper is no longer useful.
	}
	def retry() = {
		if( !isRedeliver )
			chan ! ChannelMessage { _.basicReject(deliveryTag, true) }
		else
			msgCB.map( cb => Future{ nack(); cb.retry(metaTags) } )
		body
	}
	def nack() = {
		if( deliveryTag > 0L ) chan ! ChannelMessage { _.basicReject(deliveryTag, false) }
		msgCB.map( cb => Future{ cb.nack(metaTags) } )
		body
	}

	def map[U](f: (T) => U): QMessage[U] = QMessage[U](this.deliveryTag, f(this.body), this.chan, this.isRedeliver, this.metaTags, this.msgCB)
}