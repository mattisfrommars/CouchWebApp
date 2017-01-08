package bootstrap

import javax.inject.Inject

import domain.{Role, User}
import persistence.RolesRepository
import java.sql.Timestamp
import java.util.UUID

import com.mohiva.play.silhouette.api.LoginInfo
import com.mohiva.play.silhouette.api.repositories.AuthInfoRepository

import scala.util.Try
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import com.mohiva.play.silhouette.api.util.PasswordHasherRegistry
import com.mohiva.play.silhouette.impl.providers.CredentialsProvider
import framework.services.UserService;
/**
  * Created by Nuno on 23-12-2016.
  */
class InitialData @Inject()
(rolesRepository: RolesRepository,
 userService: UserService,
 authInfoRepository: AuthInfoRepository,
 passwordHasherRegistry: PasswordHasherRegistry) {


  def insert(): Unit = {
    import play.api.libs.concurrent.Execution.Implicits.defaultContext

    val authInfo = passwordHasherRegistry.current.hash("Qwerty12_3")
    val loginInfo = LoginInfo(CredentialsProvider.ID, "actualizate.comze@gmail.com")
    val user = User(
      userID = UUID.randomUUID(),
      loginInfo = loginInfo,
      firstName = Some("Couch"),
      lastName = Some("Founder"),
      fullName = Some("Couch Founder"),
      email = Some("actualizate.comze@gmail.com"),
      avatarURL = None,
      activated = true,
      roleId = Some(1l),
      createdAt = new Timestamp(System.currentTimeMillis())
    )

    val insertInitialDataFuture = for {
      count <- rolesRepository.count() if count == 0
      _ <- rolesRepository.insert(InitialData.roles)
      _ <- userService.save(user)
      _ <- authInfoRepository.add(loginInfo,authInfo)
    } yield ()

    Try(Await.result(insertInitialDataFuture, Duration.Inf))
  }

  insert()

}

object InitialData {

  val now = new Timestamp(System.currentTimeMillis())

  def roles = Seq(
    Role(Some(1l),"Admin", now),
    Role(Some(2l),"Professional", now),
    Role(Some(3l),"Customer", now)
  )
}
