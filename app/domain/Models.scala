package domain

import java.sql.Timestamp
import java.util.UUID

import com.mohiva.play.silhouette.api.Identity
import org.joda.time.DateTime
import org.springframework.ui.context.support.UiApplicationContextUtils

import scala.concurrent.Future

/**
  * Created by Nuno on 16-12-2016.
  */

/**
  *
  * @param items
  * @param page
  * @param offset
  * @param total
  * @tparam A
  */
case class Page[A](items: Seq[A], page: Int, offset: Long, total: Long) {
  lazy val prev = Option(page - 1).filter(_ >= 0)
  lazy val next = Option(page + 1).filter(_ => (offset + items.size) < total)
}

/**
  *
  * @param id
  * @param name
  * @param createdAt
  */
case class Role(id: Option[Long],
                name: String,
                createdAt: Timestamp)

/**
  *
  * @param userID
  * @param loginInfo
  * @param firstName
  * @param lastName
  * @param fullName
  * @param email
  * @param avatarURL
  * @param activated
  * @param roleId
  * @param createdAt
  */
case class User(userID: UUID,
                loginInfo: com.mohiva.play.silhouette.api.LoginInfo,
                firstName: Option[String],
                lastName: Option[String],
                fullName: Option[String],
                email: Option[String],
                avatarURL: Option[String],
                activated: Boolean,
                roleId: Option[Long] = None,
                createdAt: Timestamp) extends Identity

/**
  *
  * @param id
  * @param firstName
  * @param lastName
  * @param fullName
  * @param email
  * @param avatarURL
  * @param activated
  * @param roleId
  * @param createdAt
  */
case class DBUser (id:  String,
                   firstName: Option[String],
                   lastName: Option[String],
                   fullName: Option[String],
                   email: Option[String],
                   avatarURL: Option[String],
                   activated: Boolean,
                   roleId: Option[Long] = Some(1),
                   createdAt: Timestamp
                  )

/**
  *
  * @param id
  * @param userID
  * @param expiry
  */
case class AuthToken(
                      id: UUID,
                      userID: UUID,
                      expiry: DateTime)

/**
  *
  * @param id
  * @param name
  * @param description
  * @param createdAt
  */
case class Speciality(id: Option[Long] = None,
                      name: String,
                      description: String,
                      createdAt: Timestamp
                     )

/**
  *
  * @param id
  * @param fullName
  * @param isApproved
  * @param userId
  * @param specialityId
  * @param createdAt
  */
case class Expert(id: Option[Long] = None,
                  fullName: String,
                  rate: Int,
                  isApproved: Boolean,
                  userId: Option[String] = None,
                  specialityId: Option[Long] = None,
                  createdAt: Timestamp
                 )

/**
  *
  * @param id
  * @param yearsOfExperience
  * @param numberOfRecommendations
  * @param numberOfCases
  * @param aboutMe
  * @param userId
  * @param createdAt
  */
case class Profile(id: Option[Long] = None,
                   yearsOfExperience: Option[Short],
                   numberOfRecommendations: Option[Short],
                   numberOfCases: Option[Short],
                   aboutMe: Option[String],
                   userId: Option[String] = None,
                   avatarUrl : Option[String] = None,
                   createdAt: Timestamp
                  )

/**
  *
  * @param id
  * @param providerID
  * @param providerKey
  */
case class LoginInfo(
                      id: Option[Long],
                      providerID: String,
                      providerKey: String
                    )

/**
  *
  * @param userID
  * @param loginInfoId
  */
case class UserLoginInfo(
                          userID: String,
                          loginInfoId: Long
                        )

/**
  *
  * @param hasher
  * @param password
  * @param salt
  * @param loginInfoId
  */
case class PasswordInfo(
                         hasher: String,
                         password: String,
                         salt: Option[String],
                         loginInfoId: Long
                       )

/**
  *
  * @param id
  * @param token
  * @param secret
  * @param loginInfoId
  */
case class OAuth1Info(
                       id: Option[Long],
                       token: String,
                       secret: String,
                       loginInfoId: Long
                     )

/**
  *
  * @param id
  * @param accessToken
  * @param tokenType
  * @param expiresIn
  * @param refreshToken
  * @param loginInfoId
  */
case class OAuth2Info(
                       id: Option[Long],
                       accessToken: String,
                       tokenType: Option[String],
                       expiresIn: Option[Int],
                       refreshToken: Option[String],
                       loginInfoId: Long
                     )

/**
  *
  * @param id
  * @param loginInfoId
  */
case class OpenIDInfo(
                       id: String,
                       loginInfoId: Long
                     )

/**
  *
  * @param id
  * @param key
  * @param value
  */
case class OpenIDAttribute(
                            id: String,
                            key: String,
                            value: String
                          )



case class Partner(id: Option[Long] = None,
                   firstName: String,
                   lastName: String,
                   email: String,
                   yearsOfExperience: Short,
                   numberOfCases: Short,
                   wasApproved: Boolean,
                   createdAt: Timestamp
                  )

case class AgendaEntry(id: Option[Long] = None,
                       isFree : Boolean,
                       startDate : Timestamp,
                       userId : String
                      )

case class Session(id: Option[Long] = None,
                   professionalNotes : Option[String] = None,
                   agendaEntryId: Long,
                   specialityId : Long,
                   professionalId: String,
                   customerId: String,
                   roomId : String,
                   sessionState : Short,
                   createdAt: Timestamp
                  )

object SessionState{
  val WAITING_FOR_START : Short = 1
  val IS_IN_SESSION : Short = 2
  val IS_AFTER_SESSION : Short = 3
  val IS_CLOSED : Short = 4
  val IS_CANCELED_BY_ADMIN : Short = 5
  val IS_CANCELED_BY_PROFESSIONAL : Short = 6
  val IS_CANCELED_BY_CUSTOMER : Short = 7
}

case class SessionView(id: Option[Long] = None,
                       professionalNotes : Option[String] = None,
                       agendaEntry: AgendaEntry,
                       speciality : Speciality,
                       professional: DBUser,
                       customer: DBUser,
                       roomId : String,
                       sessionState : Short,
                       isCancelable : Boolean,
                       dateFormatted : String,
                       createdAt: Timestamp
                  )
