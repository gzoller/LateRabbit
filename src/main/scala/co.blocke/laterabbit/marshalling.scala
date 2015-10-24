package co.blocke
package laterabbit

import scala.reflect.runtime.universe.TypeTag
import com.rabbitmq.client.AMQP.BasicProperties
import java.nio.charset.Charset

/**
  This exception is thrown when a [[RabbitUnmarshaller]] tries to
  unmarshall a message with the wrong contentType specified in the
  header.
  */
case class MismatchedContentType(received: String, expected: String) extends Exception(s"MismatchedContentType: expected '${expected}', received '${received}'")

case class ObjMarshaller[T]()(implicit tag: TypeTag[T]) extends RabbitMarshaller[T] with RabbitUnmarshaller[T] {
  val contentType = "text/plain"
  private val encoding = "UTF-8"
  protected val contentEncoding = Some(encoding)
  private val utf8 = Charset.forName(encoding)

  def marshall(value: T) = Json.toJson(value).getBytes(utf8)
  def unmarshall(value: Array[Byte], contentType: Option[String], charset: Option[String]) = 
    Json.fromJson[T](new String(value, charset map (Charset.forName) getOrElse utf8))
}

case class StringMarshaller() extends RabbitMarshaller[String] with RabbitUnmarshaller[String] {
  val contentType = "text/plain"
  private val encoding = "UTF-8"
  protected val contentEncoding = Some(encoding)
  private val utf8 = Charset.forName(encoding)

  def marshall(value: String) = value.getBytes(utf8)
  def unmarshall(value: Array[Byte], contentType: Option[String], charset: Option[String]) = 
    new String(value, charset map (Charset.forName) getOrElse utf8)
}

/**
  This trait is used to serialize messages for publication; it
  configures a property builder and sets the appropriate headers

  @see [[RabbitUnmarshaller]], [[UTF8StringMarshaller]], [[BinaryMarshaller]]
  */
trait RabbitMarshaller[T] {
  /**
    Given a value, returns a tuple of:

    - The serialized value
    - An optional string representing the text encoding (e.g. "UTF-8")
    */
  def marshall(value: T): Array[Byte]

  /**
    Returns a string representing the contentType (e.g. "application/json")
    */
  protected def contentType: String

  /**
    Returns an optional string representing the text encoding (e.g. "UTF-8")
    */
  protected def contentEncoding: Option[String]

  /**
    Given a value, and an optional property builder, returns the
    marshalled value and the property builder.

    Note, that if a property builder is provided, it is mutated by
    this method.
    */
  def setProperties(builder: BasicProperties.Builder = new BasicProperties.Builder()): BasicProperties.Builder = {
    builder.contentType(contentType)
    contentEncoding.foreach(builder.contentEncoding(_))
    builder
  }
}

/**
  This trait is used to deserialize messages from binary format for
  use in Consumers; it checks and honors the contentType / encoding
  message headers, as appropriate.
  
  @see [[RabbitMarshaller]], [[UTF8StringMarshaller]], [[BinaryMarshaller]]
  */
trait RabbitUnmarshaller[T] {
  /**
    @throws MismatchedContentType
    */
  def unmarshall(value: Array[Byte], contentType: Option[String], contentEncoding: Option[String]): T
}

/**
  Pull binary message payload raw, without any serialization. An implicit is defined in [[RabbitUnmarshaller$.binaryUnmarshaller]] and [[RabbitMarshaller$.binaryUnmarshaller]]
  */
object BinaryMarshaller extends RabbitMarshaller[Array[Byte]] with RabbitUnmarshaller[Array[Byte]] {
  protected val contentType = "application/octet-stream"
  protected val contentEncoding = None
  def marshall(value: Array[Byte]) = value
  def unmarshall(value: Array[Byte], contentType: Option[String], charset: Option[String]): Array[Byte] = value
}

/**
  Converts binary message to a UTF8 string, and back. An implicit is defined in [[RabbitUnmarshaller$.stringMarshaller]] and [[RabbitMarshaller$.stringMarshaller]]
  */
object UTF8StringMarshaller extends RabbitMarshaller[String] with RabbitUnmarshaller[String] {
  val contentType = "text/plain"
  private val encoding = "UTF-8"
  protected val contentEncoding = Some(encoding)
  private val utf8 = Charset.forName(encoding)
  def marshall(value: String) =
    value.getBytes(utf8)

  def unmarshall(value: Array[Byte], contentType: Option[String], charset: Option[String]) =
    new String(value, charset map (Charset.forName) getOrElse utf8)
}

object RabbitUnmarshaller {
  implicit val binaryUnmarshaller: RabbitUnmarshaller[Array[Byte]] = BinaryMarshaller
  implicit val stringMarshaller: RabbitUnmarshaller[String] = UTF8StringMarshaller
}

object RabbitMarshaller {
  implicit val binaryUnmarshaller: RabbitMarshaller[Array[Byte]] = BinaryMarshaller
  implicit val stringMarshaller: RabbitMarshaller[String] = UTF8StringMarshaller
}
