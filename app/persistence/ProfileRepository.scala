package persistence

import javax.inject.{Inject, Singleton}
import domain.{User, Page, Profile}
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import slick.driver.JdbcProfile
import java.sql.Timestamp
import scala.concurrent.Future



/**
  * Created by Nuno on 18-12-2016.
  */
trait ProfileComponent { self: HasDatabaseConfigProvider[JdbcProfile] =>
  import driver.api._

  class Profiles(tag: Tag) extends Table[Profile](tag, "PROFILE") {

    //implicit val dateColumnType = MappedColumnType.base[Date, Long](d => d.getTime, d => new Date(d))

    def id = column[Long]("ID", O.PrimaryKey, O.AutoInc)

    def yearsOfExperience = column[Option[Short]]("YEARSOFEXPERIENCE")

    def numberOfRecommendations = column[Option[Short]]("NUMBEROFRECOMENDATIONS")

    def numberOfCases = column[Option[Short]]("NUMBEROFCASES")

    def aboutMe = column[Option[String]]("ABOUTME")

    def userId = column[Option[String]]("USER_ID")

    def avatarUrl = column[Option[String]]("AVATARURL")

    def createdAt = column[Timestamp]("CREATEDAT")

    def * = (id.?, yearsOfExperience, numberOfRecommendations, numberOfCases, aboutMe,userId,avatarUrl, createdAt) <> (Profile.tupled, Profile.unapply _)

  }
}


@Singleton
class ProfileRepository @Inject()(protected val dbConfigProvider: DatabaseConfigProvider)
  extends ProfileComponent with UserComponent with HasDatabaseConfigProvider[JdbcProfile] {

  import driver.api._

  private val profiles = TableQuery[Profiles]
  private lazy val users = TableQuery[Users]

  /** Retrieve a profile from the id. */
  def findById(id: Long): Future[Option[Profile]] =
    db.run(profiles.filter(_.id === id).result.headOption)

  /** Retrieve a profile from the userId. */
  def findByUserId(userId: String): Future[Option[Profile]] =
    db.run(profiles.filter(_.userId === userId).result.headOption)


  /** Count all profiles. */
  def count(): Future[Int] = {
    // this should be changed to
    // db.run(computers.length.result)
    // when https://github.com/slick/slick/issues/1237 is fixed
    db.run(profiles.map(_.id).length.result)
  }

  /** Insert a new profile. */
  def insert(profile: Profile): Future[Unit] =
    db.run(profiles += profile).map(_ => ())

  /** Insert new profiles. */
  def insert(profile: Seq[Profile]): Future[Unit] =
    db.run(this.profiles ++= profile).map(_ => ())

  /** Update an profile. */
  def update(id: Long, profile: Profile): Future[Unit] = {
    val profileToUpdate: Profile = profile.copy(Some(id))
    db.run(profiles.filter(_.id === id).update(profileToUpdate)).map(_ => ())
  }

  /** Delete an expert. */
  def delete(id: Long): Future[Unit] =
    db.run(profiles.filter(_.id === id).delete).map(_ => ())

}
