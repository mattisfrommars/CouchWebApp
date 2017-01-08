package controllers

import javax.inject.Inject

import com.mohiva.play.silhouette.api.Silhouette
import framework.utils.auth.DefaultEnv
import persistence.SpecialityRepository
import play.api._
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc._
import play.api.libs.concurrent.Execution.Implicits._

class SpecialityController @Inject()(val messagesApi: MessagesApi,
                                     specialityRepository : SpecialityRepository,
                                     silhouette: Silhouette[DefaultEnv],
                                     implicit val webJarAssets: WebJarAssets
                                    )
  extends Controller with I18nSupport{

  def index(page: Int, orderBy: Int, filter: String) = silhouette.UserAwareAction.async { implicit request =>
    val specialities = specialityRepository.list(page = page, orderBy = orderBy, filter = ("%" + filter + "%"))
    specialities.map(list => Ok(views.html.services.index(list, orderBy, filter,request.identity)))
  }

  def speciality(id: Long) = play.mvc.Results.TODO

}