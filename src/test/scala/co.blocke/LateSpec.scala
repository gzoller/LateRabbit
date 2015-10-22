package co.blocke
package laterabbit
package test

import Util.await
import scala.concurrent.duration._
import org.scalatest._
import org.scalatest.fixture
import scala.collection.JavaConversions._
import scala.sys.process._
import akka.actor._
import akka.stream.ActorMaterializer
import akka.stream.scaladsl._
import akka.stream.testkit.scaladsl.TestSink
import akka.pattern.ask
import akka.util.Timeout
import com.rabbitmq.http.client._

case class Pet(name:String, age:Int)

class LateSpec extends FunSpec with Matchers with BeforeAndAfterAll with BeforeAndAfterEach { 

	println("===== NOTICE =====")
	println("This test depends on Docker being active and gumbo/world image being available.")

	implicit var system       : ActorSystem       = null
	implicit var materializer : ActorMaterializer = null

	var worldId    = ""
	var rabbitPort = 0
	var rc : ActorRef = null
	var hop : Client = null

	override def beforeAll() {
		system = ActorSystem()
		materializer = ActorMaterializer()
		val (wid,rpt,h) = worldWait()
		worldId = wid
		rabbitPort = rpt
		hop = h
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
		val apiPort = getDockerPort(worldId,15672)
		hop = new Client(s"http://${getDockerIP()}:${apiPort}/api/", "guest", "guest")
		(worldId, rabbitPort, hop)
	}

	implicit val om = ObjMarshaller[Pet]
	implicit val timeout:Timeout = 5.seconds

	describe("======================\n|  Connection Tests  |\n======================") {
		describe("Publish Functionality") {
			it("Simple Connection and Queue Declare") {
				val addrs = List(new com.rabbitmq.client.Address(getDockerIP(),rabbitPort))
				val params = ConnectionParams(addrs,"guest","guest")
				rc = system.actorOf(Props(new RabbitControl(params)))
				rc ! LateQueue("testQ",true,false,false)
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
			it("Topic publish works") {
				rc ! LateQueue("yippy",true,false,false)
				Thread.sleep(1000)
				rc ! BindTopic("yippy","foo.bar")
				Thread.sleep(1000)
				rc ! Message.topic(Pet("Fido",7),"foo.bar")
				rc ! Message.topic(Pet("Fifi",1),"foo.bar")
				Thread.sleep(1000)
				await(rc ? MessageCount("yippy")).asInstanceOf[Int] should be(2)
			}
		}
		describe("Consume Functionality") {
			it("Queue consume works") {
				val rs = RabbitSource[Pet](rc, LateQueue("testQ",true,false,false))
					.map( s => s ) // simulated "load"
					.map( _.ack )
					.runWith(TestSink.probe[Pet])
					.request(2)
					.expectNext(Pet("Fido",7),Pet("Fifi",1))
				await(rc ? MessageCount("testQ")).asInstanceOf[Int] should be(0)
			}
			it("Topic consume works") {
				val q = LateQueue("animals",true,false,false)
				val (sub,res) = RabbitSource(rc, q)
					.map( s => s ) // simulated "load"
					.map( _.ack )
					.toMat(Sink.fold(List.empty[Pet])(_ :+ _))(Keep.both)
					.run
				rc ! BindTopic(q.name,"pets.#")
				Thread.sleep(1000)
				rc ! Message.topic(Pet("Fido",7),"pets.like")
				rc ! Message.topic(Pet("Fifi",1),"pets.dontlike")
				rc ! Message.topic(Pet("Flipper",9),"fish.like")

				Thread.sleep(1000)  // make sure actorsystem isn't shut down before this registers
				system.stop(sub)
				await(res) should be(List(Pet("Fido",7),Pet("Fifi",1)))
			}
		}
		describe("Misc Functionality") {
			it("TTL works") {
				rc ! LateQueue("testQ2",true,false,false,Map("x-message-ttl"->new Integer(3000)))
				Thread.sleep(1000)
				rc ! Message.queue(Pet("Fido",7),"testQ2")
				Thread.sleep(1000)
				await(rc ? MessageCount("testQ2")).asInstanceOf[Int] should be(1)
				Thread.sleep(2500)
				await(rc ? MessageCount("testQ2")).asInstanceOf[Int] should be(0)
			}
			it("Retry works") {
				rc ! LateQueue("testQ3",true,false,false)
				Thread.sleep(1000)
				rc ! Message.queue(Pet("Fido",7),"testQ3")
				rc ! Message.queue(Pet("Fifi",1),"testQ3")
				Thread.sleep(1000)
				await(rc ? MessageCount("testQ3")).asInstanceOf[Int] should be(2)

				var nackMsg = ""
				var retryMsg = ""
				def cb = new MessageCB {
					def retry(p:List[String]):Unit = retryMsg = s"Retry Boom: $p"
					def nack(p:List[String]):Unit  = nackMsg = s"Nack Boom: $p"
				}

				val (sub,res) = RabbitSource[Pet](rc, LateQueue("testQ3",true,false,false))
					.map( m => m.copy(metaTags=List("a","b"),msgCB=Some(cb) ) )
					.map( m => if( m.body.age == 7 ) m.retry else m.ack )
					.toMat(Sink.fold(List.empty[Pet])(_ :+ _))(Keep.both)
					.run
				Thread.sleep(1000)
				system.stop(sub)
				Thread.sleep(1000)
				await(res) should be(List(Pet("Fido",7),Pet("Fido",7),Pet("Fifi",1)))
				Thread.sleep(1000)
				await(rc ? MessageCount("testQ3")).asInstanceOf[Int] should be(0)
				nackMsg should be("Nack Boom: List(a, b)")
				retryMsg should be("Retry Boom: List(a, b)")
			}
		}
	}
}