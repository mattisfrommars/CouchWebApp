package controllers

import javax.inject.Inject

import com.mohiva.play.silhouette.api.{LogoutEvent, Silhouette}
import com.mohiva.play.silhouette.impl.providers.SocialProviderRegistry
import framework.utils.auth.DefaultEnv
import play.api.i18n.{I18nSupport, Messages, MessagesApi}
import play.api.mvc._
import play.api.data.Forms._
import play.api.data._
import play.api.libs.mailer.{Email, MailerClient}

import scala.concurrent.Future

class Application @Inject() (val messagesApi: MessagesApi,
                             silhouette: Silhouette[DefaultEnv],
                             mailerClient: MailerClient,
                             implicit val webJarAssets: WebJarAssets,
                             socialProviderRegistry: SocialProviderRegistry)
  extends Controller with I18nSupport {

  def index = silhouette.UserAwareAction.async { implicit request =>
    Future.successful(Ok(views.html.index(request.identity)))
  }

  /**
    * Handles the Sign Out action.
    *
    * @return The result to display.
    */
  def signOut = silhouette.SecuredAction.async { implicit request =>
    val result = Redirect(routes.Application.index())
    silhouette.env.eventBus.publish(LogoutEvent(request.identity, request))
    silhouette.env.authenticatorService.discard(request.authenticator, result)
  }

  lazy val contactForm = Form(
    tuple(
      "subject" -> nonEmptyText,
      "email" -> email,
      "body" -> nonEmptyText
    )
  )

  def contact() = silhouette.UserAwareAction.async { implicit request =>
    Future.successful(Ok(views.html.contact(contactForm,request.identity)))
  }



  def sendContactMessage = silhouette.UserAwareAction.async { implicit request =>
    contactForm.bindFromRequest.fold(
      formWithErrors => {
        Future.successful(BadRequest(views.html.contact(formWithErrors,request.identity)))
      },
      data => {

        mailerClient.send(Email(
          subject = data._1,
          from = Messages("email.from"),
          to = Seq(Messages("email.from")),
          bodyText = Some(data._2 + " :" + data._3),
          bodyHtml = Some(data._2 + " :" + data._3)
        ))

        Future.successful(Redirect(routes.Application.index()).flashing("success"-> Messages("contact.page.form.sended")))
      })
  }

  def about() = silhouette.UserAwareAction.async { implicit request =>
    Future.successful(Ok(views.html.about(request.identity)))
  }

  def imagesRequest(name:String) = Action {
    Ok.sendFile(
      content = new java.io.File(s"/tmp/picture/$name"),
      inline = true
    )
  }

}