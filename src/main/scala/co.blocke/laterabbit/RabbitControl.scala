package co.blocke
package laterabbit

import scala.concurrent.duration._
import scala.collection.JavaConversions._
import akka.actor._
import com.thenewmotion.akka.rabbitmq._

object RabbitControl {
	val CONNECTION_ACTOR_NAME = "laterabbit_conn"
	val topicExchangeName = "amq.topic"  // make this configurable!
}
import RabbitControl._

case class GetConnection()

trait Binding
case class DeclareQueue(name:String, durable:Boolean, exclusive:Boolean, autoDelete:Boolean, args:Map[String,Object] = Map.empty[String,Object]) extends Binding
case class DeclareTopic(exchange:String, routingKey:String, queue:DeclareQueue) extends Binding

class RabbitControl( connectionParams:ConnectionParams ) extends Actor with Stash {

	private val connectionFactory = new ClusterConnectionFactory
	connectionParams.applyTo(connectionFactory)
	val connectionActor = context.actorOf(ConnectionActor.props(connectionFactory),name = CONNECTION_ACTOR_NAME)
	// val confirmedPublisher = context.actorOf(Props(new ConfirmedPublisherActor(connectionActor)),name = CONFIRMED_PUBLISHER_NAME)

	override def preStart = connectionActor ! CreateChannel(ChannelActor.props(), Some("publisher"))

	def receive = {
		case ChannelCreated(ref) =>
			context.become(withChannel(ref))
			Thread.sleep(500)  // Be sure the channel has settled, then replay stashed
			unstashAll()

		case x => 
 			stash()
	}

 	def withChannel(publishChannel: ActorRef): Receive = {
		case dQ:DeclareQueue => 
			publishChannel ! ChannelMessage { _.queueDeclare(dQ.name,dQ.durable,dQ.exclusive,dQ.autoDelete,dQ.args) }
// TODO: NEED A WAY TO ENSURE THIS TOOK HOLD BEFORE PROCESSING MORE MESSAGES!
// Maybe an Ask w/reply to sender

		case gc:GetConnection => 
			sender ! connectionActor

		case cm:ChannelMessage =>
			publishChannel ! cm
	}

	/*
	Per-Queue Message TTL (dQ.args parameter, value in ms)
	This extension determines for how long a message published to a queue can live before it is discarded by the server. The time-to-live is configured with the x-message-ttl argument to the arguments parameter of this method.
	*/

}
