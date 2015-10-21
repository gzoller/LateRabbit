package co.blocke
package laterabbit

import scala.reflect.runtime.universe.TypeTag
import co.blocke.scalajack._

object Json {

	private val sj  = ScalaJack()

	val vc = VisitorContext(
		isCanonical    = true,    // allow non-string keys in Maps--not part of JSON spec
		isValidating   = false,
		estFieldsInObj = 256,
		valClassMap    = Map.empty[String,ValClassHandler],
		hintMap        = Map("default" -> "_hint")  // per-class type hints (for nested classes)
	)

	def fromJson[T](json: String, vctx:VisitorContext = vc)(implicit tag: TypeTag[T]) = sj.read[T](json,vctx)
	private def fromJson2[T](json: String)(implicit tag: TypeTag[T]) = sj.read[T](json)
	def toJson[T](value: T, vctx:VisitorContext = vc)(implicit tag: TypeTag[T]): String = sj.render[T](value,vctx)
}