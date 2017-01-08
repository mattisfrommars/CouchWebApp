package persistence

import javax.inject.{Inject, Singleton}
import domain.{Partner,Page}
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import slick.driver.JdbcProfile
import java.sql.Timestamp
import scala.concurrent.Future

/**
  * Created by Nuno on 23-12-2016.
  */
@Singleton()
class PartnerRepository  @Inject()(protected val dbConfigProvider: DatabaseConfigProvider)
  extends HasDatabaseConfigProvider[JdbcProfile]{


  import driver.api._

  class Partners(tag: Tag) extends Table[Partner](tag, "PARTNER") {

    def id = column[Long]("ID", O.PrimaryKey, O.AutoInc)
    def firstName = column[String]("FIRSTNAME")
    def lastName = column[String]("LASTNAME")
    def email = column[String]("EMAIL")
    def yearsOfExperience = column[Short]("YEARSOFEXPERIENCE")
    def numberOfCases = column[Short]("NUMBEROFCASES")
    def wasApproved = column[Boolean]("WASAPPROVED")
    def createdAt = column[Timestamp]("CREATEDAT")

    def * = (id.?, firstName, lastName, email,yearsOfExperience,numberOfCases,wasApproved,createdAt) <> (Partner.tupled, Partner.unapply _ )

  }


  private val partners = TableQuery[Partners]

  /** Retrieve a partner from the id. */
  def findById(id: Long): Future[Option[Partner]] =
    db.run(partners.filter(_.id === id).result.headOption)

  /** Count all partners. */
  def count(): Future[Int] = {
    // this should be changed to
    // db.run(computers.length.result)
    // when https://github.com/slick/slick/issues/1237 is fixed
    db.run(partners.map(_.id).length.result)
  }

  /** Count users with a filter. */
  def count(filter: String, wasApproved:Boolean): Future[Int] = {
    db.run(partners.filter {
      partner => (partner.firstName.toLowerCase like filter.toLowerCase) && partner.wasApproved === wasApproved
    }.length.result)
  }

  /** Return a page of (Partner) */
  def list(page: Int = 0, pageSize: Int = 10, orderBy: Int = 1, filter: String = "%", approved:Boolean = false): Future[Page[(Partner)]] = {

    val offset = pageSize * page
    val query =
      (for {
        partner <- partners
        if partner.firstName.toLowerCase like filter.toLowerCase
        if partner.wasApproved === approved
      } yield (partner))
        .drop(offset)
        .take(pageSize)

    for {
      totalRows <- count(filter,approved)
      list = query.result.map { rows => rows.collect { case (partner) => (partner) } }
      result <- db.run(list)
    } yield Page(result, page, offset, totalRows)
  }


  /** Insert a new partners. */
  def insert(partner: Partner): Future[Unit] =
    db.run(partners += partner).map(_ => ())

  /** Insert new partner. */
  def insert(partners: Seq[Partner]): Future[Unit] =
    db.run(this.partners ++= partners).map(_ => ())

  /** Update an partner. */
  def update(id: Long, partner: Partner): Future[Unit] = {
    val partnerToUpdate: Partner = partner.copy(Some(id))
    db.run(partners.filter(_.id === id).update(partnerToUpdate)).map(_ => ())
  }

  /** Delete an partner. */
  def delete(id: Long): Future[Unit] =
    db.run(partners.filter(_.id === id).delete).map(_ => ())

}
