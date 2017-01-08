package framework.modules


import framework.jobs.{AuthTokenCleaner, Scheduler, SessionMailer}
import net.codingwell.scalaguice.ScalaModule
import play.api.libs.concurrent.AkkaGuiceSupport

/**
  * Created by Nuno on 20-12-2016.
  */
/**
  * The job module.
  */
class JobModule extends ScalaModule with AkkaGuiceSupport {

  /**
    * Configures the module.
    */
  def configure() = {
    bindActor[AuthTokenCleaner]("auth-token-cleaner")
    bindActor[SessionMailer]("session-mailer")
    bind[Scheduler].asEagerSingleton()
  }
}