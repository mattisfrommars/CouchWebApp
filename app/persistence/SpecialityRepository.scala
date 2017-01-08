package persistence

import javax.inject.{Inject, Singleton}
import domain.{Speciality,Page}
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import slick.driver.JdbcProfile
import java.sql.Timestamp
import scala.concurrent.Future

/**
  * Created by Nuno on 17-12-2016.
  */
trait SpecialityComponent {
  self: HasDatabaseConfigProvider[JdbcProfile] =>
 import driver.api._

  class Specialities(tag: Tag) extends Table[Speciality](tag, "SPECIALITY") {

    //implicit val dateColumnType = MappedColumnType.base[Date, Long](d => d.getTime, d => new Date(d))
    def id = column[Long]("ID", O.PrimaryKey, O.AutoInc)
    def name = column[String]("NAME")
    def description = column[String]("DESCRIPTION")
    def createdAt = column[Timestamp]("CREATEDAT")

    def * = (id.?, name, description, createdAt) <> (Speciality.tupled, Speciality.unapply _)
  }
}

@Singleton()
class SpecialityRepository @Inject()(protected val dbConfigProvider: DatabaseConfigProvider) extends
    SpecialityComponent with HasDatabaseConfigProvider[JdbcProfile]{

  import driver.api._

    private val specialities = TableQuery[Specialities]

    /** Retrieve a speciality from the id. */
    def findById(id: Long): Future[Option[Speciality]] =
      db.run(specialities.filter(_.id === id).result.headOption)

    /** Retrieve a speciality from the name. */
    def findByName(name: String): Future[Option[Speciality]] =
      db.run(specialities.filter(_.name === name).result.headOption)

    /** Count all list. */
    def count(): Future[Int] = {
      // this should be changed to
      // db.run(computers.length.result)
      // when https://github.com/slick/slick/issues/1237 is fixed
      db.run(specialities.map(_.id).length.result)
    }
    /** Count list with a filter. */
    def count(filter: String): Future[Int] = {
      db.run(specialities.filter { speciality => speciality.name.toLowerCase like filter.toLowerCase }.length.result)
    }


    /** Return a page of (User,Role) */
    def list(page: Int = 0, pageSize: Int = 10, orderBy: Int = 1, filter: String = "%"): Future[Page[(Speciality)]] = {

      val offset = pageSize * page
      val query =
        (for {
          (specialty) <- this.specialities
          if specialty.name.toLowerCase like filter.toLowerCase
        } yield specialty)
          .drop(offset)
          .take(pageSize)

      for {
        totalRows <- count(filter)
        list = query.result.map { rows => rows.collect { case (specialty) => (specialty) } }
        result <- db.run(list)
      } yield Page(result, page, offset, totalRows)
    }

    def options(): Future[Seq[(String, String)]] = {
      val query = (for {
        speciality <- this.specialities
      } yield (speciality.id, speciality.name)).sortBy(/*name*/_._2)

      db.run(query.result).map(rows => rows.map { case (id, name) => (id.toString, name) })
    }

    /** Insert a speciality user. */
    def insert(speciality: Speciality): Future[Unit] =
      db.run(specialities += speciality).map(_ => ())

    /** Insert new speciality. */
    def insert(specialities: Seq[Speciality]): Future[Unit] =
      db.run(this.specialities ++= specialities).map(_ => ())

    /** Update a speciality. */
    def update(id: Long, speciality: Speciality): Future[Unit] = {
      val userToUpdate: Speciality = speciality.copy(Some(id))
      db.run(specialities.filter(_.id === id).update(userToUpdate)).map(_ => ())
    }

    /** Delete a speciality. */
    def delete(id: Long): Future[Unit] =
      db.run(specialities.filter(_.id === id).delete).map(_ => ())

}
