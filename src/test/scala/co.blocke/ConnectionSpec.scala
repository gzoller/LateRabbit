package co.blocke
package laterabbit
package test

import Util.await
import scala.concurrent.duration._
import org.scalatest._
import org.scalatest.fixture
//import scala.collection.JavaConversions._
import scala.sys.process._
import akka.actor._
import akka.stream.ActorMaterializer
import akka.stream.scaladsl._
import akka.stream.testkit.scaladsl.TestSink
import akka.pattern.ask
import akka.util.Timeout

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
	}
	override def afterAll() {
		s"docker kill $worldId".!
		system.terminate()
	}

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
	implicit val timeout:Timeout = 5.seconds

	describe("======================\n|  Connection Tests  |\n======================") {
		describe("Publish Functionality") {
			it("Simple Connection and Queue Declare") {
				val addrs = List(new com.rabbitmq.client.Address(getDockerIP(),rabbitPort))
				val params = ConnectionParams(addrs,"guest","guest")
				rc = system.actorOf(Props(new RabbitControl(params)))
				rc ! DeclareQueue("testQ",true,false,false)
				Thread.sleep(1000)
				await(rc ? MessageCount("testQ")).asInstanceOf[Int] should be(0)
				// If this test fails, later tests will fail.
			}
			it("Queue publish works") {
				implicit val om = ObjMarshaller[Pet]
				rc ! Message.queue(Pet("Fido",7),"testQ")
				rc ! Message.queue(Pet("Fifi",1),"testQ")
				Thread.sleep(1000)
				await(rc ? MessageCount("testQ")).asInstanceOf[Int] should be(2)
			}
			// it("Topic publish works") {
			// 	rc ! Message.topic(Pet("Fido",7),"pets.like")
			// 	rc ! Message.topic(Pet("Fifi",1),"pets.nolike")
			// 	// Did this work???? Not sure... can't see any queues or messages.
			// 	// Possibly invisible?  Need to get consumer working to know.
			// 	Thread.sleep(1000)  // make sure actorsystem isn't shut down before this registers
			// }
		}
		describe("Consume Functionality") {
			it("Queue consume works") {
				RabbitSource(rc, DeclareQueue("testQ",true,false,false), BodyAs[Pet])
					.map( s => s ) // simulated "load"
					.map( _.ack )
					.runWith(TestSink.probe[Pet])
					.request(2)
					.expectNext(Pet("Fido",7),Pet("Fifi",1))
				await(rc ? MessageCount("testQ")).asInstanceOf[Int] should be(0)
			}
		}
		describe("Misc Functionality") {
			it("TTL works") {
				rc ! DeclareQueue("testQ2",true,false,false,Map("x-message-ttl"->new Integer(3000)))
				Thread.sleep(1000)
				rc ! Message.queue(Pet("Fido",7),"testQ2")
				Thread.sleep(1000)
				await(rc ? MessageCount("testQ2")).asInstanceOf[Int] should be(1)
				Thread.sleep(2500)
				await(rc ? MessageCount("testQ2")).asInstanceOf[Int] should be(0)
			}
		}
	}
}