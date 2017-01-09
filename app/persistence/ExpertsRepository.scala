package persistence

import javax.inject.{Inject, Singleton}

import domain._
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import slick.driver.JdbcProfile
import java.sql.Timestamp
import java.util.UUID

import scala.concurrent.Future

/**
  * Created by Nuno on 17-12-2016.
  */
trait ExpertsComponent { self: HasDatabaseConfigProvider[JdbcProfile] =>
  import driver.api._

  class Experts(tag: Tag) extends Table[Expert](tag, "EXPERTS") {

    //implicit val dateColumnType = MappedColumnType.base[Date, Long](d => d.getTime, d => new Date(d))

    def id = column[Long]("ID", O.PrimaryKey, O.AutoInc)

    def fullName = column[String]("FULLNAME")

    def rate = column[Int]("RATE")

    def isApproved = column[Boolean]("ISAPPROVED")

    def userId = column[Option[String]]("USER_ID")

    def specialityId = column[Option[Long]]("SPECIALITY_ID")

    def createdAt = column[Timestamp]("CREATEDAT")

    def * = (id.?, fullName,rate,isApproved, userId, specialityId, createdAt) <> (Expert.tupled, Expert.unapply _)

  }
}


@Singleton()
class ExpertsRepository @Inject()(protected val dbConfigProvider: DatabaseConfigProvider, usersRepository: UsersRepository)
  extends ExpertsComponent with UserComponent with SpecialityComponent with ProfileComponent with HasDatabaseConfigProvider[JdbcProfile] {

  import driver.api._


  private lazy val profiles = TableQuery[Profiles]
  private lazy val specialities = TableQuery[Specialities]
  private val experts = TableQuery[Experts]
  private lazy val users = TableQuery[Users]

  /** Retrieve a user from the id. */
  def findById(id: Long): Future[Option[Expert]] =
    db.run(experts.filter(_.id === id).result.headOption)

  /** Count all users. */
  def count(): Future[Int] = {
    // this should be changed to
    // db.run(computers.length.result)
    // when https://github.com/slick/slick/issues/1237 is fixed
    db.run(experts.map(_.id).length.result)
  }

  /** Count users with a filter. */
  def count(filter: String): Future[Int] = {
    db.run(experts.filter { expert => expert.fullName.toLowerCase like filter.toLowerCase }.length.result)
  }

  def countWithFlag(filter: String): Future[Int] = {
    db.run(experts.filter { expert => (expert.fullName.toLowerCase like filter.toLowerCase) && expert.isApproved === true }.length.result)
  }

  def options(userId : String): Future[Seq[(String, String)]] = {
    val query = (for {
      (expert, speciality) <- experts joinRight specialities on (_.specialityId === _.id)
    } yield (speciality.id, speciality.name,expert))
      .sortBy(/*name*/ _._2)

    db.run(query.result).map(rows => rows.map { case (id, name,ex) => (id.toString, name) })
  }

  def getApprovedFieldUser(userId : String): Future[Seq[(String, String)]] = {
    val query = (for {
      expert <- experts if expert.isApproved === true && expert.userId === Option(userId)
      speciality <- specialities.filter(_.id === expert.specialityId)
    } yield (speciality.id, speciality.name, expert.rate))
      .sortBy(/*name*/ _._2)

    db.run(query.result).map(rows => rows.map { case (id, name,rate) => (id.toString, name + " - " + rate +"€") })
  }

  def getProfessionalByExpertise(expertId: Long) : Future[(Seq[(domain.Expert,domain.Speciality)],(domain.DBUser,domain.Profile))] = {

    val query = for{
       expert <- this.experts if expert.id === expertId
       user <- this.users if user.id === expert.userId
       profile <- profiles if profile.userId === expert.userId
    } yield (user,profile)

    val q = for{
      expert <- this.experts if expert.id === expertId
      (ex,spe) <- this.experts join this.specialities on (_.specialityId === _.id) if expert.userId === ex.userId if ex.isApproved === true
    } yield (ex,spe)

    for{
      group <- db.run(query.result.head)
      result <- db.run(q.result)
    } yield {
      (result,group)
    }
  }


  def getProfessionalByProfile(profileId: Long) : Future[(Seq[(domain.Expert,domain.Speciality)],(domain.DBUser,domain.Profile))] = {

    val query = for{
      profile <- profiles if profile.id === profileId
      user <- this.users if user.id === profile.userId
    } yield (user,profile)

    val q = for{
      (expert,p) <- this.experts join profiles on (_.userId === _.userId) if p.id === profileId
      (ex,spe) <- this.experts join this.specialities on (_.specialityId === _.id) if expert.userId === ex.userId if ex.isApproved === true
    } yield (ex,spe)

    for{
      group <- db.run(query.result.head)
      result <- db.run(q.result)
    } yield {
      (result,group)
    }
  }

  /** Return a page of (Expert, Speciality) */
  def list(page: Int = 0, pageSize: Int = 10, orderBy: Int = 1, filter: String = "%"): Future[Page[(Expert, Speciality)]] = {

    val offset = pageSize * page
    val query =
      (for {
        (expert, speciality) <- experts join specialities on (_.specialityId === _.id)
        if speciality.name.toLowerCase like filter.toLowerCase
      } yield (expert, speciality))
        .drop(offset)
        .take(pageSize)

    for {
      totalRows <- count(filter)
      list = query.result.map { rows => rows.collect { case (expert, speciality) => (expert, speciality) } }
      result <- db.run(list)
    } yield Page(result, page, offset, totalRows)
  }

  /** Return a page of (Expert, Speciality) */
  def listExperts(page: Int = 0, pageSize: Int = 10, orderBy: Int = 1, filter: String = "%"): Future[Page[((Profile, DBUser), Seq[(Expert, Speciality)])]] = {

    val offset = pageSize * page

    val pro = this.profiles.drop(offset).take(pageSize).join(this.users).on(_.userId === _.id)

    val h = this.experts
      .filter(_.userId in pro.map(_._1.userId))
      .join(this.specialities).on(_.specialityId === _.id)
      .filter(_._1.isApproved === true)

    for{
      totalRows <- countWithFlag(filter)
      a <- db.run(h.result)
      b <- db.run(pro.result)
    }yield {
      val result = b.map{ n => (n,a.filter(_._1.userId == n._1.userId)) }
      Page(result,page,offset,totalRows)
    }
  }


  /** Return a page of (Expert, Speciality) */
  def listUserSpecialities(expertId: String, page: Int = 0, pageSize: Int = 10, orderBy: Int = 1, filter: String = "%"): Future[Page[(Expert, Speciality)]] = {

    val offset = pageSize * page
    val query =
      (for {
        (expert, speciality) <- experts join specialities on (_.specialityId === _.id)
        if speciality.name.toLowerCase like filter.toLowerCase
        if expert.userId === expertId
      } yield (expert, speciality))
        .drop(offset)
        .take(pageSize)

    for {
      totalRows <- count(filter)
      list = query.result.map { rows => rows.collect { case (expert, speciality) => (expert, speciality) } }
      result <- db.run(list)
    } yield Page(result, page, offset, totalRows)
  }

  /** Insert a new expert. */
  def insert(expert: Expert): Future[Unit] =
    db.run(experts += expert).map(_ => ())

  /** Insert new experts. */
  def insert(expert: Seq[Expert]): Future[Unit] =
    db.run(this.experts ++= expert).map(_ => ())

  /** Update an expert. */
  def update(id: Long, expert: Expert): Future[Unit] = {
    val expertToUpdate: Expert = expert.copy(Some(id))
    db.run(experts.filter(_.id === id).update(expertToUpdate)).map(_ => ())
  }

  /** Delete an expert. */
  def delete(id: Long): Future[Unit] =
    db.run(experts.filter(_.id === id).delete).map(_ => ())


}
