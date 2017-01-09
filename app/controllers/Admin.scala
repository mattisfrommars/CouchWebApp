package controllers

import javax.inject.Inject

import persistence._
import com.mohiva.play.silhouette.api._
import play.api.i18n.{I18nSupport, Messages, MessagesApi}
import play.api.libs.mailer.{Email, MailerClient}
import play.api.mvc.Controller
import framework.utils.auth.{DefaultEnv, WithProvider, WithRole}
import play.api.data.Forms._
import play.api.data._
import java.sql.Timestamp
import java.util.UUID

import com.mohiva.play.silhouette.api.repositories.AuthInfoRepository
import com.mohiva.play.silhouette.api.util.PasswordHasherRegistry
import com.mohiva.play.silhouette.impl.providers.CredentialsProvider
import domain.{Profile, User}
import framework.services.UserService
import play.api.libs.concurrent.Execution.Implicits._

import scala.concurrent.Future


class Admin @Inject()(val specialitiesRepository: SpecialityRepository,
                      val messagesApi: MessagesApi,
                      silhouette: Silhouette[DefaultEnv],
                      mailerClient: MailerClient,
                      partnerRepository: PartnerRepository,
                      usersRepository: UsersRepository,
                      profileRepository: ProfileRepository,
                      userService: UserService,
                      authInfoRepository: AuthInfoRepository,
                      passwordHasherRegistry: PasswordHasherRegistry,
                      implicit val webJarAssets: WebJarAssets,
                      val expertsRepository: ExpertsRepository) extends Controller with I18nSupport {

  def listSpecialities(page: Int, orderBy: Int, filter: String) = silhouette.SecuredAction(WithRole[DefaultEnv#A](1)).async { implicit request =>
    val specialities = specialitiesRepository.list(page = page, orderBy = orderBy, filter = ("%" + filter + "%"))
    specialities.map(sp => Ok(views.html.admin.speciality.list(sp, orderBy, filter,request.identity)));
  }


 lazy val specialityRegisterForm = Form(
    tuple(
      "name" -> nonEmptyText(1, 50),
      "description" -> nonEmptyText(1, 100)
    )
  )

  def indexSpeciality = silhouette.SecuredAction(WithRole[DefaultEnv#A](1)).async { implicit request =>
    Future.successful(Ok(views.html.admin.speciality.register(specialityRegisterForm, request.identity)))
  }

  def createSpeciality = silhouette.SecuredAction(WithRole[DefaultEnv#A](1)).async { implicit request =>
    specialityRegisterForm.bindFromRequest().fold(
      formWithErrors => {
       Future.successful(BadRequest(views.html.admin.speciality.register(formWithErrors,request.identity)))
      },
      speciality => {
        specialitiesRepository.insert(
          new domain.Speciality(
            name = speciality._1,
            description = speciality._2,
            createdAt = new Timestamp(System.currentTimeMillis())
          )
        )
        Future.successful(Redirect(routes.Admin.listSpecialities()).flashing("success" -> Messages("admin.speciality.new.form.success.created")))
      }
    )
  }

  lazy val specialityEditForm = Form(
    mapping(
      "id" -> optional(longNumber),
      "name" -> nonEmptyText(1, 50),
      "description" -> nonEmptyText,
      "createdAt" -> framework.utils.FormHelper.sqlTimestamp
    )(domain.Speciality.apply)(domain.Speciality.unapply)
  )

  def editSpeciality(id: Long) = silhouette.SecuredAction(WithRole[DefaultEnv#A](1)).async { implicit rs =>
    val specialityById = for {
      speciality <- specialitiesRepository.findById(id)
    } yield speciality

    specialityById.map { case speciality =>
      speciality match {
        case Some(s) => Ok(views.html.admin.speciality.edit(id, specialityEditForm.fill(s), rs.identity))
        case None => NotFound
      }
    }
  }

  def updateSpeciality(id: Long) = silhouette.SecuredAction(WithRole[DefaultEnv#A](1)).async { implicit request =>
    specialityEditForm.bindFromRequest().fold(
      formWithErrors => {
        scala.concurrent.Future {
          BadRequest(views.html.admin.speciality.edit(id, formWithErrors, request.identity))
        }
      },
      speciality => {
        for {
          _ <- specialitiesRepository.update(id, speciality)
        } yield Redirect(routes.Admin.listSpecialities()).flashing("success" -> Messages("admin.speciality.new.form.success.edited"))
      }
    )
  }


  def listSpecialitiesApplications(page: Int, orderBy: Int, filter: String) = silhouette.SecuredAction(WithRole[DefaultEnv#A](1)).async { implicit request =>
    val experts = expertsRepository.list(page = page, orderBy = orderBy, filter = ("%" + filter + "%"))
    experts.map(ex => Ok(views.html.admin.experts.list(ex, orderBy, filter,request.identity)))
  }


  def listPartnerApplication(page: Int, orderBy: Int, filter: String) = silhouette.SecuredAction(WithRole[DefaultEnv#A](1)).async { implicit request =>
    val partners = partnerRepository.list(page = page, orderBy = orderBy, filter = ("%" + filter + "%"))
    partners.map(p => Ok(views.html.admin.partner.list(p, orderBy, filter, request.identity)))
  }


  lazy val applicationEditForm = Form(
    mapping(
      "id" -> optional(longNumber),
      "fullName" -> nonEmptyText(1, 50),
      "rate" -> number,
      "isApproved" -> boolean,
      "userId" -> optional(text),
      "specialityId" -> optional(longNumber),
      "createdAt" -> framework.utils.FormHelper.sqlTimestamp
    )(domain.Expert.apply)(domain.Expert.unapply)
  )


  def editSpecialityApplications(id: Long) = silhouette.SecuredAction(WithRole[DefaultEnv#A](1)).async { implicit request =>
    val applicationById = for {
      application <- expertsRepository.findById(id)
    } yield application

    applicationById.map { case application =>
      application match {
        case Some(s) => Ok(views.html.admin.experts.edit(id, applicationEditForm.fill(s),request.identity))
        case None => NotFound
      }
    }

  }

  def updateSpecialityApplications(id: Long) = silhouette.SecuredAction(WithRole[DefaultEnv#A](1)).async { implicit request =>
    applicationEditForm.bindFromRequest().fold(
      formWithErrors => {
        scala.concurrent.Future {
          BadRequest(views.html.admin.experts.edit(id, formWithErrors,request.identity))
        }
      },
      application => {
        for {
          _ <- expertsRepository.update(id, application)
        } yield Redirect(routes.Admin.listSpecialitiesApplications()).flashing("success" -> Messages("admin.speciality.new.form.success.created"))
      }
    )
  }

  def approvePartnerApplication(partnerId: Long) = silhouette.SecuredAction(WithRole[DefaultEnv#A](1)).async { implicit request =>

    val partnerDB = for {
      partner <- partnerRepository.findById(partnerId)
    } yield (partner)

    partnerDB.map {
      case Some(p) => {
        val userDB = for{
          user <- usersRepository.findByEmail(p.email)
        } yield user

        val profile = new Profile(
          yearsOfExperience = Some(p.yearsOfExperience),
          numberOfCases = Some(p.numberOfCases),
          numberOfRecommendations = Some(0),
          aboutMe = None,
          createdAt = new Timestamp(System.currentTimeMillis()),
          userId = None
        )

        userDB.map{
          case Some(user) => {
            val userToUpdate = user.copy(roleId = Some(2l))
            val profileToInsert = profile.copy(userId = Some(user.id))

            for{
              _ <- usersRepository.updateUserDB(userToUpdate.id,userToUpdate)
              _ <- profileRepository.insert(profileToInsert)
            }yield()


            mailerClient.send(Email(
              subject = Messages("email.partner.accepted.subject"),
              from = Messages("email.from"),
              to = Seq(p.email),
              bodyText = Some(views.txt.emails.partnerApproved(p.firstName).body),
              bodyHtml = Some(views.html.emails.partnerApproved(p.firstName).body)
            ))

          }
          case _ => {

            val authInfo = passwordHasherRegistry.current.hash("Qwerty12_3")
            val loginInfo = LoginInfo(CredentialsProvider.ID, p.email)
            val user = User(
              userID = UUID.randomUUID(),
              loginInfo = loginInfo,
              firstName = Some(p.firstName),
              lastName = Some(p.lastName),
              fullName = Some(p.firstName + " " + p.lastName),
              email = Some(p.email),
              avatarURL = None,
              activated = true,
              roleId = Some(2l),
              createdAt = new Timestamp(System.currentTimeMillis())
            )

            val profileToInsert = profile.copy(userId = Some(user.userID.toString))

            val insertInitialDataFuture = for {
              _ <- userService.save(user)
              _ <- authInfoRepository.add(loginInfo,authInfo)
              _ <- profileRepository.insert(profileToInsert)
            } yield ()

            var url = routes.ForgotPasswordController.view().absoluteURL()
            mailerClient.send(Email(
              subject = Messages("email.partner.accepted.subject"),
              from = Messages("email.from"),
              to = Seq(p.email),
              bodyText = Some(views.txt.emails.partnerNonUserApproved(p.firstName, url).body),
              bodyHtml = Some(views.html.emails.partnerNonUserApproved(p.firstName, url).body)
            ))

          }
        }

        val partnerToUpdate = p.copy(wasApproved = true)
        partnerRepository.update(partnerToUpdate.id.get,partnerToUpdate)

        Redirect(routes.Admin.listPartnerApplication()).flashing("success" -> Messages("partner.new.form.approved"))
      }
      case None => Redirect(routes.Admin.listPartnerApplication()).flashing("error" -> Messages("empty.query"))
    }
  }




  def removePartnerApplication(id:Long) = silhouette.SecuredAction(WithRole[DefaultEnv#A](1)).async { implicit request =>
    for{
     _ <- partnerRepository.delete(id)
    } yield Redirect(routes.Admin.listPartnerApplication()).flashing("success" -> Messages("partner.remove.action.success"))
  }


}