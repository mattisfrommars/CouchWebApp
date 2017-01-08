package controllers

import com.mohiva.play.silhouette.api.Silhouette
import com.mohiva.play.silhouette.impl.providers.SocialProviderRegistry
import domain.Partner
import framework.utils.auth.{DefaultEnv, WithRole}
import persistence._
import play.api.data.Forms._
import play.api.data._
import play.api.i18n.{I18nSupport, Messages, MessagesApi}
import play.api.mvc._
import play.api.libs.mailer.{Email, MailerClient}
import play.api.libs.concurrent.Execution.Implicits._
import java.sql.Timestamp
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.Inject

import com.opentok._
import com.mohiva.play.silhouette.impl.authenticators.CookieAuthenticator
import framework.services.UserService

import scala.concurrent.Future

class SessionController @Inject()(
                                   mailerClient: MailerClient,
                                   agendaRepository: AgendaRepository,
                                   val messagesApi: MessagesApi,
                                   userService: UserService,
                                   usersRepository: UsersRepository,
                                   expertsRepository: ExpertsRepository,
                                   silhouette: Silhouette[DefaultEnv],
                                   sessionRepository: SessionRepository,
                                   implicit val webJarAssets: WebJarAssets,
                                   socialProviderRegistry: SocialProviderRegistry)
  extends Controller with I18nSupport {

  lazy val sessionRegisterForm = Form(
    tuple(
      "professionalId" -> nonEmptyText,
      "specialityId" -> longNumber,
      "agendaId" -> longNumber,
      "creditCardName" -> nonEmptyText(1, 100),
      "creditCardNumber" -> longNumber,
      "creditCardCVV" -> longNumber,
      "creditCardExpirationDate" -> nonEmptyText.verifying("Error", value => {
        true
      })
    )
  )

  lazy val API_KEY = 45745862
  lazy val SECRET = "ef5afeb4822cab8fc142ae384ab2665bd2040220"

  def createOpenTokInstance = {
    new OpenTok(API_KEY, SECRET)
  }

  def createOpenTokSession: String = {
    val opentok = createOpenTokInstance
    val session = opentok.createSession()
    session.getSessionId()
  }

  def generateOpenTokenForProfessional(sessionId: String): String = {
    val opentok = createOpenTokInstance
    val options = new TokenOptions.Builder().role(Role.PUBLISHER).expireTime((System.currentTimeMillis() / 1000L) + (1 * 60 * 60)).build() //One Hour
    opentok.generateToken(sessionId, options)
  }

  def generateOpenTokenForCustomer(sessionId: String): String = {
    val opentok = createOpenTokInstance
    val options = new TokenOptions.Builder().role(Role.PUBLISHER).expireTime((System.currentTimeMillis() / 1000L) + (1 * 60 * 60)).build() //One Hour
    opentok.generateToken(sessionId, options)
  }

  def index(id: Long) = silhouette.SecuredAction(WithRole[DefaultEnv#A](3)).async { implicit request =>
    val res = for {
      agenda <- agendaRepository.findById(id)
      professional <- userService.retrieve(UUID.fromString(agenda.get.userId))
      fields <- expertsRepository.getApprovedFieldUser(agenda.get.userId)
    } yield (agenda, professional, fields)

    res.map { case (agenda, professional, options) => Ok(views.html.session.index(sessionRegisterForm, agenda, professional, options, request.identity)) }
  }

  def create(id: Long) = silhouette.SecuredAction(WithRole[CookieAuthenticator](3)).async { implicit request =>
    sessionRegisterForm.bindFromRequest.fold(
      formWithErrors => {
        val res = for {
          agenda <- agendaRepository.findById(id)
          professional <- userService.retrieve(UUID.fromString(agenda.get.userId))
          fields <- expertsRepository.getApprovedFieldUser(agenda.get.userId)
        } yield (agenda, professional, fields)

        res.map { case (agenda, professional, options) => Ok(views.html.session.index(formWithErrors, agenda, professional, options, request.identity)) }
      },
      session => {

        val sessionToInsert = domain.Session(
          agendaEntryId = session._3,
          createdAt = new Timestamp(System.currentTimeMillis()),
          customerId = request.identity.userID.toString,
          professionalId = session._1,
          specialityId = session._2,
          roomId = createOpenTokSession,
          sessionState = domain.SessionState.WAITING_FOR_START
        )

        for {
          agenda <- agendaRepository.findById(session._3) if agenda.get.isFree == true
          professional <- userService.retrieve(UUID.fromString(agenda.get.userId))
          _ <- sessionRepository.insert(sessionToInsert)
          _ <- agendaRepository.update(agenda.get.id.get, agenda.get.copy(isFree = false))
        } yield {

          mailerClient.send(Email(
            subject = Messages("email.session.created.subject"),
            from = Messages("email.from"),
            to = Seq(professional.map(_.email).get.get),
            bodyText = Some(views.txt.emails.sessionProfessionalCreated(professional.map(_.firstName).get.get, agenda.map(_.startDate.toString).get).body),
            bodyHtml = Some(views.html.emails.sessionProfessionalCreated(professional.map(_.firstName).get.get, agenda.map(_.startDate.toString).get).body)
          ))

          mailerClient.send(Email(
            subject = Messages("email.session.created.subject"),
            from = Messages("email.from"),
            to = Seq(request.identity.email.get),
            bodyText = Some(views.txt.emails.sessionCustomerCreated(request.identity.firstName.get, agenda.map(_.startDate.toString).get).body),
            bodyHtml = Some(views.html.emails.sessionCustomerCreated(request.identity.firstName.get, agenda.map(_.startDate.toString).get).body)
          ))

          Redirect(routes.SessionController.sessions()).flashing("success" -> "Session created")
        }
      }
    )
  }

  /**
    * Sessions for Customer
    * Must have three identical methods.
    * CAUSES:
    * -Authorization
    * -Slightly different information
    * Benefits:
    * -Not pollute Views
    * -Clean and code focus
    * Downsides:
    * -Polymorphism denial
    * -Replication as side effect of polymorphism
    *
    * @param page
    * @param orderBy
    * @param filter
    * @return
    */
  def sessions(page: Int, orderBy: Int, filter: String,sessionStateP:Short) = silhouette.SecuredAction(WithRole[CookieAuthenticator](3)).async { implicit request =>
    val timezone = request.cookies.get("timezone").map { case (cookie) => cookie.value }.getOrElse {
      "UTC"
    }
    val result = sessionRepository.list(timezone,
      request.identity.userID.toString,
      page = page,
      orderBy = orderBy,
      filter = ("%" + filter + "%"),
      now = new Timestamp(System.currentTimeMillis()),
      sessionState = sessionStateP
    )
    result.map(sessions => Ok(views.html.session.customerSession(sessions, orderBy, filter, request.identity)))
  }

  def sessionsForProfessional(page: Int, orderBy: Int, filter: String) = silhouette.SecuredAction(WithRole[CookieAuthenticator](2)).async { implicit request =>
    val timezone = request.cookies.get("timezone").map { case (cookie) => cookie.value }.getOrElse {
      "UTC"
    }
    val result = sessionRepository.listForProfessional(timezone = timezone,
      userId = request.identity.userID.toString,
      page = page,
      orderBy = orderBy,
      filter = ("%" + filter + "%"),
      now = new Timestamp(System.currentTimeMillis())
    )
    result.map(sessions =>
      Ok(views.html.session.professionalSession(sessions, orderBy, filter, request.identity)))
  }

  def sessionsForAdmin(page: Int, orderBy: Int, filter: String) = silhouette.SecuredAction(WithRole[CookieAuthenticator](1)).async { implicit request =>
    val timezone = request.cookies.get("timezone").map { case (cookie) => cookie.value }.getOrElse {
      "UTC"
    }
    val result = sessionRepository.listForAdmin(timezone, page = page, orderBy = orderBy, filter = ("%" + filter + "%"), now = new Timestamp(System.currentTimeMillis()))
    result.map(sessions => Ok(views.html.session.customerSession(sessions, orderBy, filter, request.identity)))
  }

  def session(id: Long) = silhouette.SecuredAction.async { implicit request =>
    val result = sessionRepository.findById(id)

    result.map {
      case (Some(session)) => {
        if (!(session.professionalId == request.identity.userID.toString
          || session.customerId == request.identity.userID.toString)) {
          Redirect(routes.SessionController.sessions()).flashing("error" -> "Not Found")
        }
        var token: String = ""
        if (request.identity.roleId.getOrElse(3) == 3) {
          token = generateOpenTokenForCustomer(session.roomId)
        } else {
          token = generateOpenTokenForProfessional(session.roomId)
        }

        Ok(views.html.session.sessionRoom((API_KEY, session.roomId, token), request.identity))
      }
      case _ => Redirect(routes.SessionController.sessions()).flashing("error" -> "Not Found")
    }
  }

  def reschedule(id: Long) = play.mvc.Results.TODO

  def updateReschedule(id: Long) = play.mvc.Results.TODO

  def cancelSession(id: Long) = silhouette.SecuredAction.async { implicit request =>
    val result = sessionRepository.findById(id)

    result.map {
      case (Some(s)) => {
        sessionRepository.update(s.id.get, s.copy(sessionState = domain.SessionState.IS_CANCELED_BY_CUSTOMER))
        agendaRepository.findById(s.agendaEntryId).map {
          case (Some(a)) => {
            agendaRepository.delete(a.id.get)
            agendaRepository.insert(a.copy(id = Some(0l), isFree = true))
          }
          case _ => println("Not found")
        }

        val usersResult = usersRepository.getUsersContactInfo(s.customerId, s.professionalId)
        usersResult.map(contacts => contacts.map {
          case (Some(email), Some(name)) => {
            mailerClient.send(Email(
              subject = Messages("email.session.canceled.subject"),
              from = Messages("email.from"),
              to = Seq(email),
              bodyText = Some(views.txt.emails.sessionCanceled(name).body),
              bodyHtml = Some(views.html.emails.sessionCanceled(name).body)
            ))
          }
          case _ => println("Not found")
        })
        request.identity.roleId match {
          case Some(1) => Redirect(routes.SessionController.sessionsForAdmin()).flashing("info" -> "Session Canceled")
          case Some(2) => Redirect(routes.SessionController.sessionsForProfessional()).flashing("info" -> "Session Canceled")
          case _ => Redirect(routes.SessionController.sessions()).flashing("info" -> "Session Canceled")
        }
      }
      case _ => Redirect(routes.SessionController.sessions()).flashing("error" -> "Not Found")
    }
  }

  def closeSession(id: Long) = silhouette.SecuredAction(WithRole[CookieAuthenticator](2)).async { implicit request =>
    val result = sessionRepository.findById(id)

    result.map {
      case Some(session) => {
        sessionRepository.update(session.id.get, session.copy(sessionState = domain.SessionState.IS_CLOSED))
        Redirect(routes.SessionController.sessionsForProfessional()).flashing("success" -> "Session closed")
      }
      case _ => Redirect(routes.SessionController.sessionsForProfessional()).flashing("error" -> "Not Found")
    }
  }
}