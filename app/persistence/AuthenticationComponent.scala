package persistence

import javax.inject.{Singleton, Inject}
import scala.concurrent.Future
import domain.{LoginInfo,UserLoginInfo,PasswordInfo,OAuth1Info,OAuth2Info,OpenIDInfo,OpenIDAttribute}
import play.api.db.slick.{HasDatabaseConfigProvider, DatabaseConfigProvider}
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import slick.driver.JdbcProfile
import java.sql.Timestamp

/**
  * Created by Nuno on 19-12-2016.
  */
trait AuthenticationComponent { self: HasDatabaseConfigProvider[JdbcProfile] =>
  import driver.api._


  class LoginInfos(tag: Tag) extends Table[LoginInfo](tag, "LOGININFO") {
    def id = column[Long]("ID", O.PrimaryKey, O.AutoInc)
    def providerID = column[String]("PROVIDERID")
    def providerKey = column[String]("PROVIDERKEY")
    def * = (id.?, providerID, providerKey) <> (LoginInfo.tupled, LoginInfo.unapply)
  }

  class UserLoginInfos(tag: Tag) extends Table[UserLoginInfo](tag, "USERLOGININFO") {
    def userID = column[String]("USER_ID")
    def loginInfoId = column[Long]("LOGININFO_ID")
    def * = (userID, loginInfoId) <> (UserLoginInfo.tupled, UserLoginInfo.unapply)
  }

  class PasswordInfos(tag: Tag) extends Table[PasswordInfo](tag, "PASSWORDINFO") {
    def hasher = column[String]("HASHER")
    def password = column[String]("PASSWORD")
    def salt = column[Option[String]]("SALT")
    def loginInfoId = column[Long]("LOGININFO_ID")
    def * = (hasher, password, salt, loginInfoId) <> (PasswordInfo.tupled, PasswordInfo.unapply)
  }

  class OAuth1Infos(tag: Tag) extends Table[OAuth1Info](tag, "OAUTH1INFO") {
    def id = column[Long]("ID", O.PrimaryKey, O.AutoInc)
    def token = column[String]("TOKEN")
    def secret = column[String]("SECRET")
    def loginInfoId = column[Long]("LOGININFO_ID")
    def * = (id.?, token, secret, loginInfoId) <> (OAuth1Info.tupled, OAuth1Info.unapply)
  }

  class OAuth2Infos(tag: Tag) extends Table[OAuth2Info](tag, "OAUTH2INFO") {
    def id = column[Long]("ID", O.PrimaryKey, O.AutoInc)
    def accessToken = column[String]("ACCESSTOKEN")
    def tokenType = column[Option[String]]("TOKENTYPE")
    def expiresIn = column[Option[Int]]("EXPIRESIN")
    def refreshToken = column[Option[String]]("REFRESHTOKEN")
    def loginInfoId = column[Long]("LOGININFO_ID")
    def * = (id.?, accessToken, tokenType, expiresIn, refreshToken, loginInfoId) <> (OAuth2Info.tupled, OAuth2Info.unapply)
  }

  class OpenIDInfos(tag: Tag) extends Table[OpenIDInfo](tag, "OPENIDINFO") {
    def id = column[String]("ID", O.PrimaryKey)
    def loginInfoId = column[Long]("LOGININFO_ID")
    def * = (id, loginInfoId) <> (OpenIDInfo.tupled, OpenIDInfo.unapply)
  }

  class OpenIDAttributes(tag: Tag) extends Table[OpenIDAttribute](tag, "OPENIDATTRIBUTES") {
    def id = column[String]("ID")
    def key = column[String]("KEY")
    def value = column[String]("VALUE")
    def * = (id, key, value) <> (OpenIDAttribute.tupled, OpenIDAttribute.unapply)
  }


  val LoginInfos = TableQuery[LoginInfos]
  val UserLoginInfos = TableQuery[UserLoginInfos]
  val PasswordInfos = TableQuery[PasswordInfos]
  val OAuth1Infos = TableQuery[OAuth1Infos]
  val OAuth2Infos = TableQuery[OAuth2Infos]
  val OpenIDInfos = TableQuery[OpenIDInfos]
  val OpenIDAttributes = TableQuery[OpenIDAttributes]

  def loginInfoQuery(loginInfo: com.mohiva.play.silhouette.api.LoginInfo) =
    LoginInfos.filter(dbLoginInfo => dbLoginInfo.providerID === loginInfo.providerID && dbLoginInfo.providerKey === loginInfo.providerKey)

}
