package framework.utils.auth

import com.mohiva.play.silhouette.api.Env
import com.mohiva.play.silhouette.impl.authenticators.CookieAuthenticator
import domain.User
/**
  * Created by Nuno on 19-12-2016.
  */
trait DefaultEnv extends Env {
  type I = User
  type A = CookieAuthenticator
}
