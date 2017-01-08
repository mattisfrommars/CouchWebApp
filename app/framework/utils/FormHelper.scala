package framework.utils

import play.api.data.Mapping
import play.api.data.Forms._
import play.api.data.format._
import play.api.data.format.Formats._
import java.sql.Timestamp

/**
  * Created by Nuno on 17-12-2016.
  */
object FormHelper {

  val sqlTimestamp: Mapping[Timestamp] = of[Timestamp]

  def sqlTimestamp(pattern: String): Mapping[Timestamp] = of[Timestamp] as sqlTimestampFormat(pattern)

  implicit def sqlTimestampFormat: Formatter[Timestamp] = sqlTimestampFormat("yyyy-mm-dd HH:MM:SS")

  implicit def sqlTimestampFormat(pattern: String): Formatter[Timestamp] = new Formatter[Timestamp] {
    override val format = Some("format.date", Seq(pattern))

    def bind(key: String, data: Map[String, String]) =
      dateFormat(pattern).bind(key, data).right.map(d => new Timestamp(d.getTime))

    def unbind(key: String, value: Timestamp) = dateFormat(pattern).unbind(key, value)
  }
}
