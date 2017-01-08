package framework.jobs

import java.sql.Timestamp
import javax.inject.Inject

import akka.actor.Actor.Receive
import akka.actor._
import com.mohiva.play.silhouette.api.util.Clock
import framework.jobs.SessionMailer.VerifySessions
import framework.utils.Logger
import persistence.SessionRepository
import play.api.i18n.{I18nSupport, Messages, MessagesApi}
import play.api.libs.mailer.{Email, MailerClient}

import scala.concurrent.ExecutionContext.Implicits.global

/**
  * Created by Nuno on 31-12-2016.
  */
class SessionMailer @Inject() ( mailerClient: MailerClient,
                                sessionRepository: SessionRepository,
                                val messagesApi: MessagesApi,
                                clock: Clock)
  extends Actor with Logger with I18nSupport{

  override def receive: Receive = {
    case VerifySessions => {

      val now = new Timestamp(System.currentTimeMillis());
      val tenMinutesFromNow = new Timestamp(System.currentTimeMillis()+(10*60*1000));
      val msg = new StringBuffer("\n")
      val results = sessionRepository.sessionsInDateRange(now,tenMinutesFromNow)

      results.map(list => list.map{
        case (session,customer,professional) => {

          mailerClient.send(Email(
            subject = Messages("email.session.starting.subject"),
            from = Messages("email.from"),
            to = Seq(customer.email.get),
            bodyText = Some(views.txt.emails.sessionAlmostStart(customer.fullName.getOrElse("User")).body),
            bodyHtml = Some(views.html.emails.sessionAlmostStart(customer.fullName.getOrElse("User")).body)
          ))

          mailerClient.send(Email(
            subject = Messages("email.session.starting.subject"),
            from = Messages("email.from"),
            to = Seq(professional.email.get),
            bodyText = Some(views.txt.emails.sessionAlmostStart(professional.fullName.getOrElse("User")).body),
            bodyHtml = Some(views.html.emails.sessionAlmostStart(professional.fullName.getOrElse("User")).body)
          ))

          sessionRepository.update(session.id.get, session.copy(sessionState = domain.SessionState.IS_IN_SESSION))

        }
        case _ => msg.append("Error")
      })

    }
  }

}


/**
  * The companion object.
  */
object SessionMailer {
  case object VerifySessions
}