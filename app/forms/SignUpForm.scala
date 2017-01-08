package forms


import play.api.data.Form
import play.api.data.Forms._

/**
  * Created by Nuno on 20-12-2016.
  */
object SignUpForm {

  /**
    * A play framework form.
    */
  val form = Form(
    mapping(
      "firstName" -> nonEmptyText,
      "lastName" -> nonEmptyText,
      "email" -> email,
      "password" -> nonEmptyText
    )(Data.apply)(Data.unapply)
  )

  /**
    * The form data.
    *
    * @param firstName The first name of a user.
    * @param lastName The last name of a user.
    * @param email The email of the user.
    * @param password The password of the user.
    */
  case class Data(
                   firstName: String,
                   lastName: String,
                   email: String,
                   password: String)
}
