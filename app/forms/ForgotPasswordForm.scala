package forms

import play.api.data.Forms._
import play.api.data._

/**
  * Created by Nuno on 20-12-2016.
  */
object ForgotPasswordForm {

  /**
    * A play framework form.
    */
  val form = Form(
    "email" -> email
  )
}
