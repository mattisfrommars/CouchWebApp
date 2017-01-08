package persistence

import javax.inject.Inject

import domain.AgendaEntry
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.driver.JdbcProfile
import java.sql.Timestamp

import scala.concurrent.Future
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import java.time.{ZoneId, ZonedDateTime}
import java.time.temporal.ChronoUnit.DAYS
import java.util.Date

/**
  * Created by Nuno on 25-12-2016.
  */

trait AgendaComponent { self: HasDatabaseConfigProvider[JdbcProfile] =>
  import driver.api._

 class AgendaEntries(tag: Tag) extends Table[AgendaEntry](tag, "AGENDAENTRY") {

    def id = column[Long]("ID", O.PrimaryKey, O.AutoInc)

    def isFree = column[Boolean]("ISFREE")

    def startDate = column[Timestamp]("STARTDATE")

    def userId = column[String]("USER_ID")

    def * = (id.?, isFree, startDate, userId) <> (AgendaEntry.tupled, AgendaEntry.unapply _)

  }

}

class AgendaRepository @Inject()(protected val dbConfigProvider: DatabaseConfigProvider)
  extends AgendaComponent with HasDatabaseConfigProvider[JdbcProfile] {

  import driver.api._

  private lazy val agendas = TableQuery[AgendaEntries]

  /** Retrieve a Agenda from the id. */
  def findById(id: Long): Future[Option[AgendaEntry]] =
    db.run(agendas.filter(_.id === id).result.headOption)

  /** Count all Agenda. */
  def count(): Future[Int] = {
    // this should be changed to
    // db.run(computers.length.result)
    // when https://github.com/slick/slick/issues/1237 is fixed
    db.run(agendas.map(_.id).length.result)
  }

  /** Insert a new Agenda. */
  def insert(agenda: AgendaEntry): Future[Unit] =
    db.run(agendas += agenda).map(_ => ())

  /** Insert new Agenda. */
  def insert(agendas: Seq[AgendaEntry]): Future[Unit] =
    db.run(this.agendas ++= agendas).map(_ => ())

  def listEntries(startDate: Timestamp, endDate: Timestamp, userId: String): Future[Seq[AgendaEntry]] = {

    val query = for {
      entry <- agendas if (entry.userId === userId) && (entry.startDate >= startDate && entry.startDate < endDate)
    } yield entry

    db.run(query.result)
  }

  def listEntriesForCustomer(userId: String, startDate: Timestamp): Future[Map[ZonedDateTime, Seq[AgendaEntry]]] = {

    val q = (for {
      entry <- agendas if entry.userId === userId && entry.startDate >= startDate && entry.isFree === true
    } yield entry).sortBy(_.startDate)


    for {
      result <- db.run(q.result)
    } yield {
      result.groupBy(e => ZonedDateTime.ofInstant(e.startDate.toInstant, ZoneId.of("UTC")).truncatedTo(DAYS))
        .toList.sortBy(_._1.getDayOfYear)
        .take(2)
        .map{case (key,values) => (key,values.sortBy(_.startDate.getTime))}
        .toMap
    }
  }

  /** Update an Agenda. */
  def update(id: Long, agenda: AgendaEntry): Future[Unit] = {
    val profileToUpdate: AgendaEntry = agenda.copy(Some(id))
    db.run(agendas.filter(_.id === id).update(profileToUpdate)).map(_ => ())
  }

  /** Delete an Agenda. */
  def delete(id: Long): Future[Unit] =
    db.run(agendas.filter(_.id === id).delete).map(_ => ())


}
