package persistence

import javax.inject.{Inject, Singleton}

import scala.concurrent.Future
import domain.{Session, _}
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import slick.driver.JdbcProfile
import java.sql.Timestamp
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.UUID

import framework.services.UserService

/**
  * Created by Nuno on 29-12-2016.
  */
trait SessionComponent {
  self: HasDatabaseConfigProvider[JdbcProfile] =>

  import driver.api._

  class Sessions(tag: Tag) extends Table[domain.Session](tag, "SESSION") {
    def id = column[Long]("ID", O.PrimaryKey, O.AutoInc)

    def professionalNotes = column[Option[String]]("PROFESSIONALNOTES")

    def agendaEntryId = column[Long]("AGENDAENTRY_ID")

    def specialityId = column[Long]("SPECIALITY_ID")

    def professionalId = column[String]("PROFESSIONAL_ID")

    def customerId = column[String]("CUSTOMER_ID")

    def roomId = column[String]("ROOM_ID")

    def sessionState = column[Short]("SESSION_STATE")

    def createdAt = column[Timestamp]("CREATEDAT")

    def * = (id.?, professionalNotes, agendaEntryId, specialityId, professionalId, customerId, roomId, sessionState, createdAt) <> (domain.Session.tupled, domain.Session.unapply _)
  }

}

@Singleton()
class SessionRepository @Inject()(protected val dbConfigProvider: DatabaseConfigProvider)
  extends SessionComponent
    with ExpertsComponent with SpecialityComponent with UserComponent with AgendaComponent with HasDatabaseConfigProvider[JdbcProfile] {

  import driver.api._

  private val sessions = TableQuery[Sessions]
  private val agendas = TableQuery[AgendaEntries]
  private val users = TableQuery[Users]
  private val experts = TableQuery[Experts]
  private val specialities = TableQuery[Specialities]

  /** Retrieve a session from the id. */
  def findById(id: Long): Future[Option[domain.Session]] =
    db.run(sessions.filter(_.id === id).result.headOption)

  /** Count all session. */
  def count(): Future[Int] = {
    // this should be changed to
    // db.run(computers.length.result)
    // when https://github.com/slick/slick/issues/1237 is fixed
    db.run(sessions.map(_.id).length.result)
  }


  def count(userId: String, now: Timestamp): Future[Int] = {
    db.run(sessions
      .filter { session =>
        session.createdAt >= now &&
          session.customerId === userId
      }.length.result)
  }

  /** Return a page of (sessions) */
  def list(timezone: String, userId: String, page: Int = 0, pageSize: Int = 10, orderBy: Int = 1, filter: String = "%", now: Timestamp, sessionState: Short = domain.SessionState.WAITING_FOR_START): Future[Page[(domain.SessionView)]] = {

    val offset = pageSize * page

    val query =
      (for {
        session <- sessions if session.customerId === userId if session.sessionState === sessionState
        customer <- users if customer.id === session.customerId
        (((professional, agenda), _), spe) <- users join agendas on (_.id === _.userId) join experts on (_._1.id === _.userId) join specialities on (_._2.specialityId === _.id) if professional.id === session.professionalId && agenda.id === session.agendaEntryId && spe.id === session.specialityId
      } yield (session, agenda, customer, professional, spe))
        .sortBy(_._2.startDate)
        .drop(offset)
        .take(pageSize)

    for {
      totalRows <- count(userId, now)
      list = query.result.map { rows =>
        rows.collect { case (session, agenda, customer, professional, spe) => domain.SessionView(
          id = session.id,
          professionalNotes = session.professionalNotes,
          agendaEntry = agenda,
          speciality = spe,
          professional = professional,
          customer = customer,
          roomId = session.roomId,
          sessionState = session.sessionState,
          isCancelable = isCancelable(agenda.startDate, timezone),
          dateFormatted = toLocalDate(agenda.startDate, timezone),
          createdAt = session.createdAt
        )
        }
      }
      result <- db.run(list)
    } yield {
      Page(result, page, offset, totalRows)
    }
  }

  /** Return a page of (sessions) */
  def listHistoric(timezone: String, userId: String, page: Int = 0, pageSize: Int = 10, orderBy: Int = 1, filter: String = "%", now: Timestamp, sessionState: Short = domain.SessionState.WAITING_FOR_START): Future[Page[(domain.SessionView)]] = {

    val offset = pageSize * page

    val query =
      (for {
        session <- sessions if session.customerId === userId if session.sessionState >= domain.SessionState.IS_AFTER_SESSION && session.sessionState <= domain.SessionState.IS_CLOSED
        customer <- users if customer.id === session.customerId
        (((professional, agenda), _), spe) <- users join agendas on (_.id === _.userId) join experts on (_._1.id === _.userId) join specialities on (_._2.specialityId === _.id) if professional.id === session.professionalId && agenda.id === session.agendaEntryId && spe.id === session.specialityId
      } yield (session, agenda, customer, professional, spe))
        .sortBy(_._2.startDate)
        .drop(offset)
        .take(pageSize)

    for {
      totalRows <- count(userId, now)
      list = query.result.map { rows =>
        rows.collect { case (session, agenda, customer, professional, spe) => domain.SessionView(
          id = session.id,
          professionalNotes = session.professionalNotes,
          agendaEntry = agenda,
          speciality = spe,
          professional = professional,
          customer = customer,
          roomId = session.roomId,
          sessionState = session.sessionState,
          isCancelable = isCancelable(agenda.startDate, timezone),
          dateFormatted = toLocalDate(agenda.startDate, timezone),
          createdAt = session.createdAt
        )
        }
      }
      result <- db.run(list)
    } yield {
      Page(result, page, offset, totalRows)
    }
  }

  /** Return a page of (sessions) */
  def listCanceled(timezone: String, userId: String, page: Int = 0, pageSize: Int = 10, orderBy: Int = 1, filter: String = "%", now: Timestamp, sessionState: Short = domain.SessionState.IS_CLOSED): Future[Page[(domain.SessionView)]] = {

    val offset = pageSize * page

    val query =
      (for {
        session <- this.sessions if session.customerId === userId && session.sessionState > sessionState
        customer <- users if customer.id === session.customerId
        (((professional, agenda), _), spe) <- users join agendas on (_.id === _.userId) join experts on (_._1.id === _.userId) join specialities on (_._2.specialityId === _.id) if professional.id === session.professionalId && agenda.id === session.agendaEntryId && spe.id === session.specialityId
      } yield (session, agenda, customer, professional, spe))
        .sortBy(_._2.startDate)
        .drop(offset)
        .take(pageSize)

    for {
      totalRows <- count(userId, now)
      list = query.result.map { rows =>
        rows.collect { case (session, agenda, customer, professional, spe) => domain.SessionView(
          id = session.id,
          professionalNotes = session.professionalNotes,
          agendaEntry = agenda,
          speciality = spe,
          professional = professional,
          customer = customer,
          roomId = session.roomId,
          sessionState = session.sessionState,
          isCancelable = isCancelable(agenda.startDate, timezone),
          dateFormatted = toLocalDate(agenda.startDate, timezone),
          createdAt = session.createdAt
        )
        }
      }
      result <- db.run(list)
    } yield {
      Page(result, page, offset, totalRows)
    }
  }


  /** Return a page of (sessions) */
  def listForProfessional(timezone: String, userId: String, page: Int = 0, pageSize: Int = 10, orderBy: Int = 1, filter: String = "%", now: Timestamp, sessionState: Short = domain.SessionState.WAITING_FOR_START): Future[Page[(domain.SessionView)]] = {

    val offset = pageSize * page

    val query =
      (for {
        session <- sessions if session.professionalId === userId if session.sessionState === sessionState
        customer <- users if customer.id === session.customerId
        (((professional, agenda), _), spe) <- users join agendas on (_.id === _.userId) join experts on (_._1.id === _.userId) join specialities on (_._2.specialityId === _.id) if professional.id === session.professionalId && agenda.id === session.agendaEntryId && spe.id === session.specialityId
      } yield (session, agenda, customer, professional, spe))
        .sortBy(_._2.startDate)
        .drop(offset)
        .take(pageSize)

    for {
      totalRows <- count(userId, now)
      list = query.result.map { rows =>
        rows.collect { case (session, agenda, customer, professional, spe) => domain.SessionView(
          id = session.id,
          professionalNotes = session.professionalNotes,
          agendaEntry = agenda,
          speciality = spe,
          professional = professional,
          customer = customer,
          roomId = session.roomId,
          sessionState = session.sessionState,
          isCancelable = isCancelable(agenda.startDate, timezone),
          dateFormatted = toLocalDate(agenda.startDate, timezone),
          createdAt = session.createdAt
        )
        }
      }
      result <- db.run(list)
    } yield {
      Page(result, page, offset, totalRows)
    }
  }

  /** Return a page of (sessions) */
  def listForProfessionalHistoric(timezone: String, userId: String, page: Int = 0, pageSize: Int = 10, orderBy: Int = 1, filter: String = "%", now: Timestamp, sessionState: Short = domain.SessionState.WAITING_FOR_START): Future[Page[(domain.SessionView)]] = {

    val offset = pageSize * page

    val query =
      (for {
        session <- sessions if session.professionalId === userId if session.sessionState === domain.SessionState.IS_CLOSED
        customer <- users if customer.id === session.customerId
        (((professional, agenda), _), spe) <- users join agendas on (_.id === _.userId) join experts on (_._1.id === _.userId) join specialities on (_._2.specialityId === _.id) if professional.id === session.professionalId && agenda.id === session.agendaEntryId && spe.id === session.specialityId
      } yield (session, agenda, customer, professional, spe))
        .sortBy(_._2.startDate)
        .drop(offset)
        .take(pageSize)

    for {
      totalRows <- count(userId, now)
      list = query.result.map { rows =>
        rows.collect { case (session, agenda, customer, professional, spe) => domain.SessionView(
          id = session.id,
          professionalNotes = session.professionalNotes,
          agendaEntry = agenda,
          speciality = spe,
          professional = professional,
          customer = customer,
          roomId = session.roomId,
          sessionState = session.sessionState,
          isCancelable = isCancelable(agenda.startDate, timezone),
          dateFormatted = toLocalDate(agenda.startDate, timezone),
          createdAt = session.createdAt
        )
        }
      }
      result <- db.run(list)
    } yield {
      Page(result, page, offset, totalRows)
    }
  }


  /** Return a page of (sessions) */
  def listForProfessionalCanceled(timezone: String, userId: String, page: Int = 0, pageSize: Int = 10, orderBy: Int = 1, filter: String = "%", now: Timestamp, sessionState: Short = domain.SessionState.WAITING_FOR_START): Future[Page[(domain.SessionView)]] = {

    val offset = pageSize * page

    val query =
      (for {
        session <- sessions if session.professionalId === userId if session.sessionState > domain.SessionState.IS_CLOSED
        customer <- users if customer.id === session.customerId
        (((professional, agenda), _), spe) <- users join agendas on (_.id === _.userId) join experts on (_._1.id === _.userId) join specialities on (_._2.specialityId === _.id) if professional.id === session.professionalId && agenda.id === session.agendaEntryId && spe.id === session.specialityId
      } yield (session, agenda, customer, professional, spe))
        .sortBy(_._2.startDate)
        .drop(offset)
        .take(pageSize)

    for {
      totalRows <- count(userId, now)
      list = query.result.map { rows =>
        rows.collect { case (session, agenda, customer, professional, spe) => domain.SessionView(
          id = session.id,
          professionalNotes = session.professionalNotes,
          agendaEntry = agenda,
          speciality = spe,
          professional = professional,
          customer = customer,
          roomId = session.roomId,
          sessionState = session.sessionState,
          isCancelable = isCancelable(agenda.startDate, timezone),
          dateFormatted = toLocalDate(agenda.startDate, timezone),
          createdAt = session.createdAt
        )
        }
      }
      result <- db.run(list)
    } yield {
      Page(result, page, offset, totalRows)
    }
  }

  /** Return a page of (sessions) */
  def listForProfessionalNotPayed(timezone: String, userId: String, page: Int = 0, pageSize: Int = 10, orderBy: Int = 1, filter: String = "%", now: Timestamp, sessionState: Short = domain.SessionState.WAITING_FOR_START): Future[Page[(domain.SessionView)]] = {

    val offset = pageSize * page

    val query =
      (for {
        session <- sessions if session.professionalId === userId if session.sessionState === domain.SessionState.IS_AFTER_SESSION
        customer <- users if customer.id === session.customerId
        (((professional, agenda), _), spe) <- users join agendas on (_.id === _.userId) join experts on (_._1.id === _.userId) join specialities on (_._2.specialityId === _.id) if professional.id === session.professionalId && agenda.id === session.agendaEntryId && spe.id === session.specialityId
      } yield (session, agenda, customer, professional, spe))
        .sortBy(_._2.startDate)
        .drop(offset)
        .take(pageSize)

    for {
      totalRows <- count(userId, now)
      list = query.result.map { rows =>
        rows.collect { case (session, agenda, customer, professional, spe) => domain.SessionView(
          id = session.id,
          professionalNotes = session.professionalNotes,
          agendaEntry = agenda,
          speciality = spe,
          professional = professional,
          customer = customer,
          roomId = session.roomId,
          sessionState = session.sessionState,
          isCancelable = isCancelable(agenda.startDate, timezone),
          dateFormatted = toLocalDate(agenda.startDate, timezone),
          createdAt = session.createdAt
        )
        }
      }
      result <- db.run(list)
    } yield {
      Page(result, page, offset, totalRows)
    }
  }


  /** Return a page of (sessions) */
  def listForAdmin(timezone: String, page: Int = 0, pageSize: Int = 10, orderBy: Int = 1, filter: String = "%", now: Timestamp, sessionState: Short = domain.SessionState.WAITING_FOR_START): Future[Page[(domain.SessionView)]] = {

    val offset = pageSize * page

    val query =
      (for {
        session <- sessions if session.sessionState === sessionState
        customer <- users if customer.id === session.customerId
        (((professional, agenda), _), spe) <- users join agendas on (_.id === _.userId) join experts on (_._1.id === _.userId) join specialities on (_._2.specialityId === _.id) if professional.id === session.professionalId && agenda.id === session.agendaEntryId && spe.id === session.specialityId
      } yield (session, agenda, customer, professional, spe))
        .sortBy(_._2.startDate)
        .drop(offset)
        .take(pageSize)

    for {
      totalRows <- count("%", now)
      list = query.result.map { rows =>
        rows.collect { case (session, agenda, customer, professional, spe) => domain.SessionView(
          id = session.id,
          professionalNotes = session.professionalNotes,
          agendaEntry = agenda,
          speciality = spe,
          professional = professional,
          customer = customer,
          roomId = session.roomId,
          sessionState = session.sessionState,
          isCancelable = isCancelable(agenda.startDate, timezone),
          dateFormatted = toLocalDate(agenda.startDate, timezone),
          createdAt = session.createdAt
        )
        }
      }
      result <- db.run(list)
    } yield {
      Page(result, page, offset, totalRows)
    }
  }

  /** Return a page of (sessions) */
  def listForAdminHistoric(timezone: String, page: Int = 0, pageSize: Int = 10, orderBy: Int = 1, filter: String = "%", now: Timestamp, sessionState: Short = domain.SessionState.IS_CLOSED): Future[Page[(domain.SessionView)]] = {

    val offset = pageSize * page

    val query =
      (for {
        session <- sessions if session.sessionState === sessionState
        customer <- users if customer.id === session.customerId
        (((professional, agenda), _), spe) <- users join agendas on (_.id === _.userId) join experts on (_._1.id === _.userId) join specialities on (_._2.specialityId === _.id) if professional.id === session.professionalId && agenda.id === session.agendaEntryId && spe.id === session.specialityId
      } yield (session, agenda, customer, professional, spe))
        .sortBy(_._2.startDate)
        .drop(offset)
        .take(pageSize)

    for {
      totalRows <- count("%", now)
      list = query.result.map { rows =>
        rows.collect { case (session, agenda, customer, professional, spe) => domain.SessionView(
          id = session.id,
          professionalNotes = session.professionalNotes,
          agendaEntry = agenda,
          speciality = spe,
          professional = professional,
          customer = customer,
          roomId = session.roomId,
          sessionState = session.sessionState,
          isCancelable = isCancelable(agenda.startDate, timezone),
          dateFormatted = toLocalDate(agenda.startDate, timezone),
          createdAt = session.createdAt
        )
        }
      }
      result <- db.run(list)
    } yield {
      Page(result, page, offset, totalRows)
    }
  }

  /** Return a page of (sessions) */
  def listForAdminNotPayed(timezone: String, page: Int = 0, pageSize: Int = 10, orderBy: Int = 1, filter: String = "%", now: Timestamp, sessionState: Short = domain.SessionState.IS_AFTER_SESSION): Future[Page[(domain.SessionView)]] = {

    val offset = pageSize * page

    val query =
      (for {
        session <- sessions if session.sessionState === sessionState
        customer <- users if customer.id === session.customerId
        (((professional, agenda), _), spe) <- users join agendas on (_.id === _.userId) join experts on (_._1.id === _.userId) join specialities on (_._2.specialityId === _.id) if professional.id === session.professionalId && agenda.id === session.agendaEntryId && spe.id === session.specialityId
      } yield (session, agenda, customer, professional, spe))
        .sortBy(_._2.startDate)
        .drop(offset)
        .take(pageSize)

    for {
      totalRows <- count("%", now)
      list = query.result.map { rows =>
        rows.collect { case (session, agenda, customer, professional, spe) => domain.SessionView(
          id = session.id,
          professionalNotes = session.professionalNotes,
          agendaEntry = agenda,
          speciality = spe,
          professional = professional,
          customer = customer,
          roomId = session.roomId,
          sessionState = session.sessionState,
          isCancelable = isCancelable(agenda.startDate, timezone),
          dateFormatted = toLocalDate(agenda.startDate, timezone),
          createdAt = session.createdAt
        )
        }
      }
      result <- db.run(list)
    } yield {
      Page(result, page, offset, totalRows)
    }
  }

  /** Return a page of (sessions) */
  def listForAdminCanceled(timezone: String, page: Int = 0, pageSize: Int = 10, orderBy: Int = 1, filter: String = "%", now: Timestamp, sessionState: Short = domain.SessionState.IS_CLOSED): Future[Page[(domain.SessionView)]] = {

    val offset = pageSize * page

    val query =
      (for {
        session <- sessions if session.sessionState > sessionState
        customer <- users if customer.id === session.customerId
        (((professional, agenda), _), spe) <- users join agendas on (_.id === _.userId) join experts on (_._1.id === _.userId) join specialities on (_._2.specialityId === _.id) if professional.id === session.professionalId && agenda.id === session.agendaEntryId && spe.id === session.specialityId
      } yield (session, agenda, customer, professional, spe))
        .sortBy(_._2.startDate)
        .drop(offset)
        .take(pageSize)

    for {
      totalRows <- count("%", now)
      list = query.result.map { rows =>
        rows.collect { case (session, agenda, customer, professional, spe) => domain.SessionView(
          id = session.id,
          professionalNotes = session.professionalNotes,
          agendaEntry = agenda,
          speciality = spe,
          professional = professional,
          customer = customer,
          roomId = session.roomId,
          sessionState = session.sessionState,
          isCancelable = isCancelable(agenda.startDate, timezone),
          dateFormatted = toLocalDate(agenda.startDate, timezone),
          createdAt = session.createdAt
        )
        }
      }
      result <- db.run(list)
    } yield {
      Page(result, page, offset, totalRows)
    }
  }


  lazy val formatter = DateTimeFormatter.ofPattern("HH.mm dd.MM.yyyy VV O Z");

  private def toLocalDate(start: Timestamp, zoneId: String): String = {
    formatter.format(start.toLocalDateTime.atZone(ZoneId.of(zoneId)))
  }

  private def isCancelable(time: Timestamp, timeZone: String): Boolean = {
    val now = java.time.ZonedDateTime.now(ZoneId.of(timeZone))
    val future = time.toLocalDateTime.atZone(ZoneId.of(timeZone))
    val difference = ChronoUnit.HOURS.between(now.toLocalDateTime, future.toLocalDateTime)
    difference >= 48
  }

  /** Insert a new session. */
  def insert(session: domain.Session): Future[Unit] =
    db.run(sessions += session).map(_ => ())

  /** Insert new sessions. */
  def insert(sessions: Seq[domain.Session]): Future[Unit] =
    db.run(this.sessions ++= sessions).map(_ => ())

  /** Update a session. */
  def update(id: Long, session: domain.Session): Future[Unit] = {
    val sessionToUpdate: domain.Session = session.copy(Some(id))
    db.run(sessions.filter(_.id === id).update(sessionToUpdate)).map(_ => ())
  }

  /** Delete a session. */
  def delete(id: Long): Future[Unit] =
    db.run(sessions.filter(_.id === id).delete).map(_ => ())


  def sessionsInDateRange(start: Timestamp, end: Timestamp): Future[Seq[(domain.Session, domain.DBUser, domain.DBUser)]] = {

    var query = for {
      (session, agenda) <- this.sessions join this.agendas on (_.agendaEntryId === _.id) if agenda.startDate > start && agenda.startDate < end
      (costumer) <- this.users if costumer.id === session.customerId
      (professional) <- this.users if professional.id === session.professionalId
    } yield (session, costumer, professional)

    db.run(query.result)
  }


  def getRescheduleInfo(sessionId: Long, timezone: String): Future[Option[(domain.Session, AgendaEntry, DBUser, DBUser, Speciality)]] = {

    val query = for {
      session <- this.sessions if session.id === sessionId
      customer <- users if customer.id === session.customerId
      (((professional, agenda), _), spe) <- users join agendas on (_.id === _.userId) join experts on (_._1.id === _.userId) join specialities on (_._2.specialityId === _.id) if professional.id === session.professionalId && agenda.id === session.agendaEntryId && spe.id === session.specialityId
    } yield (session, agenda, customer, professional, spe)


    for {
      result <- db.run(query.result.headOption)
    } yield result
  }





}
