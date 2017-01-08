package persistence

import java.util.UUID

import domain.AuthToken
import org.joda.time.DateTime

import scala.collection.mutable
import scala.concurrent.Future

/**
  * Created by Nuno on 20-12-2016.
  */
class AuthTokenRepositoryImpl extends AuthTokenRepository {


  /**
    * Finds a token by its ID.
    *
    * @param id The unique token ID.
    * @return The found token or None if no token for the given ID could be found.
    */
  def find(id: UUID) = Future.successful(AuthTokenRepositoryImpl.tokens.get(id))

  /**
    * Finds expired tokens.
    *
    * @param dateTime The current date time.
    */
  def findExpired(dateTime: DateTime) = Future.successful {
    AuthTokenRepositoryImpl.tokens.filter {
      case (id, token) =>
        token.expiry.isBefore(dateTime)
    }.values.toSeq
  }

  /**
    * Saves a token.
    *
    * @param token The token to save.
    * @return The saved token.
    */
  def save(token: AuthToken) = {
    AuthTokenRepositoryImpl.tokens += (token.id -> token)
    Future.successful(token)
  }

  /**
    * Removes the token for the given ID.
    *
    * @param id The ID for which the token should be removed.
    * @return A future to wait for the process to be completed.
    */
  def remove(id: UUID) = {
    AuthTokenRepositoryImpl.tokens -= id
    Future.successful(())
  }
}

/**
  * The companion object.
  */
object AuthTokenRepositoryImpl {

  /**
    * The list of tokens.
    */
  val tokens: mutable.HashMap[UUID, AuthToken] = mutable.HashMap()
}
