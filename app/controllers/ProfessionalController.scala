package controllers

import persistence.{ExpertsRepository, ProfileRepository, SpecialityRepository}
import javax.inject.Inject

import domain.Expert
import play.api.mvc._
import play.api.data._
import play.api.data.Forms._
import play.api.i18n.{I18nSupport, Messages, MessagesApi}
import java.sql.Timestamp
import java.util.UUID

import com.mohiva.play.silhouette.api.Silhouette
import framework.utils.auth.{DefaultEnv, WithRole}
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.data.format.Formats._

import scala.concurrent.Future

class ProfessionalController @Inject()(val specialitiesRepository: SpecialityRepository,
                                       val messagesApi: MessagesApi,
                                       silhouette: Silhouette[DefaultEnv],
                                       implicit val webJarAssets: WebJarAssets,
                                       profileRepository: ProfileRepository,
                                       val expertsRepository: ExpertsRepository) extends Controller with I18nSupport{

  def index(page: Int, orderBy: Int, filter: String) = silhouette.UserAwareAction.async { implicit request =>
    val experts = expertsRepository.listExperts(page = page, orderBy = orderBy, filter = ("%" + filter + "%"))
    experts.map(list =>
      Ok(views.html.professional.index(list,orderBy,filter,request.identity)))
  }

  def professionalInfo(expertId: Long) = silhouette.UserAwareAction.async { implicit request =>

    val result = expertsRepository.getProfessionalByExpertise(expertId);

    result.map(info => Ok(views.html.professional.profile(info,request.identity)) )
      .recover{ case _ => NotFound }
  }

  def listApplicationExpertise(page: Int, orderBy: Int, filter: String) = silhouette.SecuredAction(WithRole[DefaultEnv#A](2)).async{ implicit request =>
    val experts = expertsRepository.listUserSpecialities(request.identity.userID.toString,page = page, orderBy = orderBy, filter = ("%" + filter + "%"))
    experts.map(ex => Ok(views.html.professional.listExpertiseFields(ex,orderBy,filter, request.identity)))
  }


  val applicationRegisterForm = Form(
    single(
      "specialityId" -> longNumber
    )
  )

  def newApplication = silhouette.SecuredAction(WithRole[DefaultEnv#A](2)).async { implicit request =>
    expertsRepository.options(request.identity.userID.toString).map(options => Ok(views.html.professional.newApplication(applicationRegisterForm,options, request.identity)))
  }

  def registerApplication = silhouette.SecuredAction(WithRole[DefaultEnv#A](2)).async { implicit request =>
    applicationRegisterForm.bindFromRequest().fold(
      formWithErrors => {
        expertsRepository.options(request.identity.userID.toString).map(options => BadRequest(views.html.professional.newApplication(formWithErrors,options, request.identity)))
      },
      application => {

        for{
          _ <- expertsRepository.insert(
            new Expert(
              fullName = request.identity.firstName.getOrElse("User"),
              isApproved = false,
              specialityId = Option(application),
              createdAt = new Timestamp(System.currentTimeMillis()),
              userId = Some(request.identity.userID.toString)
            ))
        } yield Redirect(routes.ProfessionalController.listApplicationExpertise()).flashing("success" -> Messages("professional.expertise.fields.btn.action"))
      }
    )
  }

  def professionalProfile = silhouette.SecuredAction(WithRole[DefaultEnv#A](2)).async { implicit request =>
    val result = profileRepository.findByUserId(request.identity.userID.toString)

    result.map(theProfile => Ok(views.html.professional.profileAccount(theProfile,request.identity)))

  }


  private lazy val editProfileForm = Form(
    tuple(
      "numberOfCases" -> shortNumber,
      "yearsOfExperience" -> shortNumber,
      "aboutMe" -> nonEmptyText(1,300)
    )
  )

  def editprofessionalProfile = silhouette.SecuredAction(WithRole[DefaultEnv#A](2)).async { implicit request =>
    Future.successful(Ok(views.html.professional.editProfile(editProfileForm,request.identity)))
  }

  def updateprofessionalProfile = silhouette.SecuredAction(WithRole[DefaultEnv#A](2)).async { implicit request =>
    editProfileForm.bindFromRequest().fold(
      formWithErrors => {
        Future.successful(BadRequest(views.html.professional.editProfile(formWithErrors,request.identity)))
      },
    data => {
      val result = profileRepository.findByUserId(request.identity.userID.toString)

      for{
        profile <- result.map{case(Some(p)) => p }
        _ <- profileRepository.update(profile.id.get, profile.copy(yearsOfExperience = Some(data._2), numberOfCases = Some(data._1), aboutMe = Some(data._3)))
      } yield Redirect(routes.ProfessionalController.professionalProfile).flashing("success" -> "Ok")
    })
  }


  def updateprofessionalPhotoProfile = silhouette.SecuredAction(parse.multipartFormData) { request =>
    request.body.file("picture").map { picture =>
      import java.io.File
      val filename = picture.filename
      val contentType = picture.contentType
      picture.ref.moveTo(new File(s"/tmp/picture/$filename"))
      val result = profileRepository.findByUserId(request.identity.userID.toString)

      for{
        profile <- result.map{case(Some(p)) => p }
      } yield {
        profileRepository.update(profile.id.get, profile.copy(avatarUrl = Some(filename)))
      }

      Redirect(routes.ProfessionalController.professionalProfile).flashing("success" -> "Ok")

    }.getOrElse {
      Redirect(routes.ProfessionalController.professionalProfile).flashing(
        "error" -> "Missing file")
    }
  }

}