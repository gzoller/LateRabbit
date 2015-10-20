package co.blocke
package laterabbit
package test

import org.scalatest._
import org.scalatest.fixture
//import scala.collection.JavaConversions._
import scala.sys.process._
import akka.actor._
import akka.stream.ActorMaterializer
import akka.stream.scaladsl._

case class Pet(name:String, age:Int)

class ConnectionSpec extends FunSpec with Matchers with BeforeAndAfterAll with BeforeAndAfterEach { 

	println("===== NOTICE =====")
	println("This test depends on Docker being active and gumbo/world image being available.")

	implicit var system       : ActorSystem       = null
	implicit var materializer : ActorMaterializer = null

	var worldId    = ""
	var rabbitPort = 0
	var rc : ActorRef = null

	override def beforeAll() {
		system = ActorSystem()
		materializer = ActorMaterializer()
		val (wid,rpt) = worldWait()
		worldId = wid
		rabbitPort = rpt

		// ch = harness.facilities.rabbitmq.get.createChannel
		// ch.queueDeclare(inHot, true, false, false, null)  // ensure queue is present
		// ch.queueDeclare(inCold, true, false, false, null)  
		// ch.queueDeclare(smsHiQ, true, false, false, null)
		// ch.queueDeclare(emailHiQ, true, false, false, null)
		// ch.queueDeclare(fbHiQ, true, false, false, null) 
		// ch.queueDeclare(pushHiQ, true, false, false, null) 

		// eQ = new QueueingConsumer(ch) 
		// ch.basicConsume(emailHiQ, true, eQ)
		// sQ = new QueueingConsumer(ch) 
		// ch.basicConsume(smsHiQ, true, sQ)
		// fQ = new QueueingConsumer(ch) 
		// ch.basicConsume(fbHiQ, true, fQ)
		// pQ = new QueueingConsumer(ch) 
		// ch.basicConsume(pushHiQ, true, pQ)
	}
	override def afterAll() {
		// ch.basicCancel(pQ.getConsumerTag)
		// s"docker kill $worldId".!
		system.terminate()
	}
	override def beforeEach() {
		// ch.queuePurge(pushHiQ)
	}

/*
	def rabbitWait( qc:QueueingConsumer, limit:Int = 40 ) = {
		var period = 0
		var done = false
		var retVal : Option[String] = None
		while( !done && period < limit ) {
			period +=1
			print('.')
			val delivery = qc.nextDelivery(500)
			if( delivery != null ) {
				done = true
				println('!')
				retVal = Some(new String(delivery.getBody))
				// qc.getChannel.basicAck(delivery.getEnvelope().getDeliveryTag(), false)
			}
		}
		if( period == limit ) println('X')
		retVal
	}
	*/

	def getDockerPort(cid:String, port:Int) = (s"docker port $cid $port".!!).split(':')(1).trim.toInt
	def getDockerIP() = {
		val machineName = (Seq("sh", "-c", "docker-machine active 2>/dev/null").!!).trim
		(s"docker-machine ip $machineName".!!).trim
	}

	def worldWait( limit:Int = 30 ) = {
		val pwd = System.getProperty("user.dir")
		val worldId    = s"$pwd/src/test/resources/startWorld.sh".!!
		rabbitPort = getDockerPort(worldId, 5672)
		Thread.sleep(3000) // let the World come up and settle down
		(worldId, rabbitPort)
	}

	implicit val om = ObjMarshaller[Pet]
	describe("======================\n|  Connection Tests  |\n======================") {
		describe("Publish Functionality") {
			it("Simple Connection and Queue Declare") {
				val addrs = List(new com.rabbitmq.client.Address(getDockerIP(),rabbitPort))
				val params = ConnectionParams(addrs,"guest","guest")
				rc = system.actorOf(Props(new RabbitControl(params)))
				rc ! DeclareQueue("testQ",true,false,false)
				Thread.sleep(1000)
				// If this test fails, later tests will fail.

				// println("Hit Return to end...")
				// scala.io.StdIn.readLine()
			}
			it("Queue publish works") {
				implicit val om = ObjMarshaller[Pet]
				rc ! Message.queue(Pet("Fido",7),"testQ")
				rc ! Message.queue(Pet("Fifi",1),"testQ")
			}
			// it("Topic publish works") {
			// 	rc ! Message.topic(Pet("Fido",7),"pets.like")
			// 	rc ! Message.topic(Pet("Fifi",1),"pets.nolike")
			// 	// Did this work???? Not sure... can't see any queues or messages.
			// 	// Possibly invisible?  Need to get consumer working to know.
			// 	Thread.sleep(1000)  // make sure actorsystem isn't shut down before this registers
			// }
			it("Queue consume works") {
println("Go See...")
Thread.sleep(45000)
println("ok!")
				RabbitSource(rc, DeclareQueue("testQ",true,false,false), BodyAs[Pet])
					.map( s => { println("::::::::::::::::::::::::::: S: "+s); Thread.sleep(7000); s })
					.map( _.ack )
					.runWith(Sink.ignore)
				Thread.sleep(30000)
			}
		}
	}
}