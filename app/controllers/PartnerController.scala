package controllers

import javax.inject.Inject

import com.mohiva.play.silhouette.api.Silhouette
import com.mohiva.play.silhouette.impl.providers.SocialProviderRegistry
import domain.Partner
import framework.utils.auth.DefaultEnv
import persistence.PartnerRepository
import play.api.data.Forms._
import play.api.data._
import play.api.i18n.{I18nSupport, Messages, MessagesApi}
import play.api.mvc._
import play.api.libs.mailer.{Email, MailerClient}
import play.api.libs.concurrent.Execution.Implicits._
import java.sql.Timestamp;
import scala.concurrent.Future

class PartnerController @Inject() (
                         mailerClient: MailerClient,
                         partnerRepository: PartnerRepository,
                         val messagesApi: MessagesApi,
                         silhouette: Silhouette[DefaultEnv],
                         implicit val webJarAssets: WebJarAssets,
                         socialProviderRegistry: SocialProviderRegistry)
  extends Controller with I18nSupport {

  lazy val partnerForm = Form(
    tuple(
      "firstName" -> nonEmptyText,
      "lastName" -> nonEmptyText,
      "email" -> email,
      "yearsOfExperience" -> shortNumber,
      "numberOfCases" -> shortNumber
    )
  )

  def index() = silhouette.UserAwareAction.async { implicit request =>
    Future.successful(Ok(views.html.partner.index(partnerForm, request.identity)))
  }


  def registerPartner() = silhouette.UserAwareAction.async { implicit request =>
    partnerForm.bindFromRequest.fold(
      formWithErrors => {
        Future.successful(BadRequest(views.html.partner.index(formWithErrors, request.identity)))
      },
      partner => {
        for {
          _ <- partnerRepository.insert(new Partner(firstName =partner._1,
            lastName=partner._2,
            email=partner._3,
            yearsOfExperience = partner._4,
            numberOfCases = partner._5,
            wasApproved =false,
            createdAt = new Timestamp(System.currentTimeMillis())))
        } yield {
          mailerClient.send(Email(
            subject = Messages("email.partner.register.subject"),
            from = Messages("email.from"),
            to = Seq(partner._3),
            bodyText = Some(views.txt.emails.partnerRegister(partner._1).body),
            bodyHtml = Some(views.html.emails.partnerRegister(partner._1).body)
          ))
          Redirect(routes.Application.index()).flashing("info" -> Messages("partner.new.form.submitted"))
        }
      }
    )
  }
}