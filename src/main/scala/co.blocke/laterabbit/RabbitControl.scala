package co.blocke
package laterabbit

import Util.await
import scala.concurrent.duration._
import scala.collection.JavaConversions._
import akka.actor._
import com.thenewmotion.akka.rabbitmq._
import akka.pattern.ask
import akka.util.Timeout
import com.rabbitmq.client._

object RabbitControl {
	val CONNECTION_ACTOR_NAME = "laterabbit_conn"
	val topicExchangeName = "amq.topic"  // make this configurable!
}
import RabbitControl._

case class GetConnection()
case class MessageCount(queueName:String)
case class BindTopic(queueName:String, routingKey:String, exchange:String=topicExchangeName)

trait Binding
case class LateQueue(name:String, durable:Boolean, exclusive:Boolean, autoDelete:Boolean, args:Map[String,Object] = Map.empty[String,Object]) extends Binding
case class LateTopic(routingKey:String, queue:LateQueue, exchange:String=topicExchangeName) extends Binding

class RabbitControl( connectionParams:ConnectionParams ) extends Actor with Stash {

	private val connectionFactory = new ClusterConnectionFactory
	connectionParams.applyTo(connectionFactory)
	val connectionActor = context.actorOf(ConnectionActor.props(connectionFactory),name = CONNECTION_ACTOR_NAME)

	override def preStart = connectionActor ! CreateChannel(ChannelActor.props(), Some("publisher"))
	override def postStop: Unit = {} // Don't restart the child actors!!!

	def receive = {
		case ChannelCreated(ref) =>
			context.become(withChannel(ref))
			Thread.sleep(500)  // Be sure the channel has settled, then replay stashed
			unstashAll()

		case x => 
 			stash()
	}

 	def withChannel(publishChannel: ActorRef): Receive = {
 		// Declare queue
		case dQ:LateQueue => 
			publishChannel ! ChannelMessage { _.queueDeclare(dQ.name,dQ.durable,dQ.exclusive,dQ.autoDelete,dQ.args) }
// TODO: NEED A WAY TO ENSURE THIS TOOK HOLD BEFORE PROCESSING MORE MESSAGES!
// Maybe an Ask w/reply to sender

		case gc:GetConnection => 
			sender ! connectionActor

		case cm:ChannelMessage =>
			publishChannel ! cm

		// Mainly for publishers to ensure binding exists, as consumers (RabbitSource) will auto-bind
		case b:BindTopic => publishChannel ! ChannelMessage{ _.queueBind(b.queueName,b.exchange,b.routingKey) }

		case mc:MessageCount => 
			implicit val timeout:Timeout = 5.seconds
			val mysender = sender
			publishChannel ! ChannelMessage( {mysender ! _.queueDeclarePassive(mc.queueName).getMessageCount}, false)
	}
}
