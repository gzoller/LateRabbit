package co.blocke
package laterabbit

import scala.concurrent.{Future, Await}
import scala.concurrent.duration._

object Util {
	def await[T](f: Future[T], duration: FiniteDuration = 5.seconds) = {
		Await.result(f, duration)
	}
}