package framework.jobs

import akka.actor.{ ActorRef, ActorSystem }
import com.google.inject.Inject
import com.google.inject.name.Named
import com.typesafe.akka.extension.quartz.QuartzSchedulerExtension


/**
  * Created by Nuno on 20-12-2016.
  */

/**
  * Schedules the jobs.
  */
class Scheduler @Inject() (
                            system: ActorSystem,
                            @Named("auth-token-cleaner") authTokenCleaner: ActorRef,
                            @Named("session-mailer") sessionMailer : ActorRef) {

  QuartzSchedulerExtension(system).schedule("AuthTokenCleaner", authTokenCleaner, AuthTokenCleaner.Clean)
  QuartzSchedulerExtension(system).schedule("SessionMailer", sessionMailer, SessionMailer.VerifySessions)
  QuartzSchedulerExtension(system).schedule("SessionMailerOne", sessionMailer, SessionMailer.VerifySessions)

  authTokenCleaner ! AuthTokenCleaner.Clean
  sessionMailer ! SessionMailer.VerifySessions
}