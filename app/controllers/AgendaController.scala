package controllers

import java.sql.Timestamp
import java.time.{Period, ZoneId}
import java.util.{Calendar, Date}
import javax.inject.Inject

import com.mohiva.play.silhouette.api.Silhouette
import domain.AgendaEntry
import framework.utils.auth.{DefaultEnv, WithRole}
import persistence.AgendaRepository
import play.api._
import play.api.mvc._
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.concurrent.Execution.Implicits._
import org.apache.commons.lang3.time.DateUtils
import play.api.libs.json._
import play.api.libs.functional.syntax._
import play.api.libs.json.Reads._
import java.time.temporal.ChronoUnit.DAYS

import scala.concurrent.Future

class
AgendaController @Inject()(val messagesApi: MessagesApi,
                                 agendaRepository: AgendaRepository,
                                 silhouette: Silhouette[DefaultEnv],
                                 implicit val webJarAssets: WebJarAssets
                                )
  extends Controller with I18nSupport {


  case class AgendaEntryToJson(id: Long, startTimeUtc: Timestamp, state: Boolean)

  case class AgendaToJson(numberOfDays: Int, startDateUtc: Timestamp, entries: Seq[AgendaEntryToJson])

  implicit lazy val agendaEntriesWrite: Writes[AgendaEntryToJson] = (
    (JsPath \ "id").write[Long] and
      (JsPath \ "startTimeUtc").write[Timestamp] and
      (JsPath \ "state").write[Boolean]
    ) (unlift(AgendaEntryToJson.unapply))

  implicit lazy val agendaEntryWrite: Writes[AgendaToJson] = (
    (JsPath \ "numberOfDays").write[Int] and
      (JsPath \ "startDateUtc").write[Timestamp] and
      (JsPath \ "entries").write(Writes.seq[AgendaEntryToJson](agendaEntriesWrite))
    ) (unlift(AgendaToJson.unapply))

  implicit def ZonedDateTimeToTimestamp(date: java.time.ZonedDateTime) = {
    Timestamp.from(date.toInstant)
  }

  def index = silhouette.SecuredAction(WithRole[DefaultEnv#A](2)).async { implicit request =>
    Future.successful(Ok(views.html.agenda.index(request.identity)))
  }


  def userAgenda(start: String = "") = silhouette.SecuredAction(WithRole[DefaultEnv#A](2)).async { implicit request =>

    var startTimestamp: java.time.ZonedDateTime = null

    if (start.isEmpty) {
      val now = java.time.ZonedDateTime.now()
      startTimestamp = now.truncatedTo(DAYS)
    } else {
      startTimestamp = java.time.ZonedDateTime.parse(start)
    }

    val endDate = startTimestamp.plus(Period.ofDays(4))

    val user = request.identity.userID.toString

    val res = for {
      list <- agendaRepository.listEntries(startTimestamp, endDate, request.identity.userID.toString)
    } yield list

    res
      .map(s => Ok(Json.toJson(AgendaToJson(4, startTimestamp, s.map(entry => AgendaEntryToJson(entry.id.get, entry.startDate, entry.isFree))))))
  }

  case class ReadJsonAgendaEntriesHelper(id: Long,
                                         state: Boolean,
                                         isToErase: Boolean,
                                         startTimeLocal: String,
                                         startTimeLocalId: String,
                                         startTimeUtc: String)

  case class ReadJsonAgendaHelper(header: String, Date: String, entriesForDay: Seq[ReadJsonAgendaEntriesHelper])

  implicit val readAgendaEntry: Reads[ReadJsonAgendaEntriesHelper] = (
    (JsPath \ "id").read[Long] and
      (JsPath \ "state").read[Boolean] and
      (JsPath \ "isToErase").read[Boolean] and
      (JsPath \ "startTimeLocal").read[String] and
      (JsPath \ "startTimeLocalId").read[String] and
      (JsPath \ "startTimeUtc").read[String]
    ) (ReadJsonAgendaEntriesHelper.apply _)

  implicit val readAgenda: Reads[ReadJsonAgendaHelper] = (
    (JsPath \ "header").read[String] and
      (JsPath \ "Date").read[String] and
      (JsPath \ "entriesForDay").read[Seq[ReadJsonAgendaEntriesHelper]]
    ) (ReadJsonAgendaHelper.apply _)

  def storeEntries = silhouette.SecuredAction(BodyParsers.parse.json) { implicit request =>
    val agendaResult = request.body.validate[List[ReadJsonAgendaHelper]]

    agendaResult.fold(
      errors => {
        BadRequest(Json.obj("status" -> "KO", "message" -> JsError.toJson(errors)))
      },
      agenda => {
        val toInsert = agenda
          .filter(!_.entriesForDay.isEmpty)
          .map(_.entriesForDay).map(entries => entries.filter(el => el.id == 0 && el.isToErase==false).map(entry =>
          AgendaEntry(isFree = entry.state,
            userId = request.identity.userID.toString,
            startDate = java.time.ZonedDateTime.parse(entry.startTimeUtc)
          )
        ))

        val toDelete = agenda
          .filter(!_.entriesForDay.isEmpty)
          .map(_.entriesForDay).map(entries => entries.filter(el => el.isToErase && el.id!=0))

        toInsert.foreach(list => list.foreach(element => agendaRepository.insert(element)))
        toDelete.foreach(list => list.foreach(element => agendaRepository.delete(element.id)))

        Ok(Json.obj("status" -> "OK", "message" -> ("Saved")))
      }
    )
  }

  def delete() = play.mvc.Results.TODO

  case class AgendaMapToJson(date:Timestamp,entries: Seq[AgendaEntry])

  implicit lazy val agendaEntriesJson : Writes[AgendaEntry] = (
    (JsPath \ "id").write[Option[Long]] and
      (JsPath \ "isFree" ).write[Boolean] and
      (JsPath \ "startDate").write[Timestamp] and
      (JsPath \ "userId").write[String]
    )(unlift(AgendaEntry.unapply))

  implicit lazy val agendaMapEntryWrite: Writes[AgendaMapToJson] = (
    (JsPath \ "date").write[Timestamp] and
      (JsPath \ "entries").write(Writes.seq[AgendaEntry](agendaEntriesJson))
    ) (unlift(AgendaMapToJson.unapply))


  def detail(id: String,start:String) = Action.async  { implicit request =>
    val timezone = request.cookies.get("timezone").map{ case(cookie) => cookie.value}.getOrElse{"UTC"}
    var startDate : java.time.ZonedDateTime = null;

    if(start.isEmpty){
     startDate =  java.time.ZonedDateTime.now(ZoneId.of(timezone)).plusMinutes(10)
    }else{
      startDate = java.time.ZonedDateTime.parse(start)
    }

    val res = agendaRepository.listEntriesForCustomer(id,startDate)
    res.map(list =>
      Ok(Json.toJson(list.map{case (key,values) => AgendaMapToJson(key,values)}))
    )
  }

  def create() = play.mvc.Results.TODO


  private def getDateTimeNow = {

    val startDay = Calendar.getInstance()
    startDay.set(Calendar.HOUR_OF_DAY, 0)
    startDay.set(Calendar.MINUTE, 0);
    startDay.set(Calendar.SECOND, 0);
    startDay.set(Calendar.MILLISECOND, 0);

    startDay
  }


  private def toCalendar(date: Date) = {
    var cal = Calendar.getInstance()
    cal.setTime(date)
    cal
  }

}