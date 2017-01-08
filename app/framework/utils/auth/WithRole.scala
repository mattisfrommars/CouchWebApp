package framework.utils.auth

import com.mohiva.play.silhouette.api.{Authenticator, Authorization}
import domain.User
import play.api.mvc.Request

import scala.concurrent.Future

/**
  * Created by Nuno on 23-12-2016.
  */
case class WithRole [A <: Authenticator](role: Int) extends Authorization[User, A] {

  /**
    * Indicates if a user is authorized to access an action.
    *
    * @param user          The usr object.
    * @param authenticator The authenticator instance.
    * @param request       The current request.
    * @tparam B The type of the request body.
    * @return True if the user is authorized, false otherwise.
    */
  override def isAuthorized[B](user: User, authenticator: A)(
    implicit
    request: Request[B]): Future[Boolean] = {

    Future.successful(user.roleId.getOrElse(3) == role)
  }
}
