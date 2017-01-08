package framework.services


import java.sql.Timestamp
import java.util.UUID
import javax.inject.Inject

import com.mohiva.play.silhouette.api.LoginInfo
import com.mohiva.play.silhouette.impl.providers.CommonSocialProfile
import domain.User
import persistence.UsersRepository
import play.api.libs.concurrent.Execution.Implicits._

import scala.concurrent.Future

/**
  * Created by Nuno on 19-12-2016.
  */
class UserServiceImpl @Inject() (userRepository: UsersRepository) extends UserService {


  /**
    * Saves a user.
    *
    * @param user The user to save.
    * @return The saved user.
    */
  override def save(user: User): Future[User] = userRepository.save(user)

  /**
    * Saves the social profile for a user.
    *
    * If a user exists for this profile then update the user, otherwise create a new user with the given profile.
    *
    * @param profile The social profile to save.
    * @return The user for whom the profile was saved.
    */
  override def save(profile: CommonSocialProfile): Future[User] = {

    userRepository.find(profile.loginInfo).flatMap {
      case Some(user) => // Update user with profile
        userRepository.save(user.copy(
          firstName = profile.firstName,
          lastName = profile.lastName,
          fullName = profile.fullName,
          email = profile.email,
          avatarURL = profile.avatarURL
        ))
      case None => // Insert a new user
        userRepository.save(User(
          userID = UUID.randomUUID(),
          loginInfo = profile.loginInfo,
          firstName = profile.firstName,
          lastName = profile.lastName,
          fullName = profile.fullName,
          email = profile.email,
          avatarURL = profile.avatarURL,
          activated = true,
          createdAt = new Timestamp(System.currentTimeMillis())
        ))
    }
  }


  override def retrieve(loginInfo: LoginInfo): Future[Option[User]] = userRepository.find(loginInfo)

  /**
    * Retrieves a user that matches the specified ID.
    *
    * @param id The ID to retrieve a user.
    * @return The retrieved user or None if no user could be retrieved for the given ID.
    */
  override def retrieve(id: UUID): Future[Option[User]] = userRepository.find(id);
}
