package persistence

import javax.inject.{Singleton, Inject}
import scala.concurrent.Future
import domain.Role
import play.api.db.slick.{HasDatabaseConfigProvider, DatabaseConfigProvider}
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import slick.driver.JdbcProfile
import java.sql.Timestamp
/**
  * Created by Nuno on 16-12-2016.
  */

trait RolesComponent { self: HasDatabaseConfigProvider[JdbcProfile] =>
  import driver.api._

  class Roles(tag: Tag) extends Table[Role](tag, "ROLE") {
    def id = column[Long]("ID", O.PrimaryKey, O.AutoInc)
    def name = column[String]("NAME")
    def createdAt = column[Timestamp]("CREATEDAT")
    def * = (id.?, name, createdAt) <> (Role.tupled, Role.unapply _)
  }
}

@Singleton()
class RolesRepository @Inject()(protected val dbConfigProvider: DatabaseConfigProvider) extends RolesComponent
  with HasDatabaseConfigProvider[JdbcProfile] {

  import driver.api._

  val roles = TableQuery[Roles]

  /** Construct the Map[String,String] needed to fill a select options set */
  def options(): Future[Seq[(String, String)]] = {
    val query = (for {
      role <- roles
    } yield (role.id, role.name, role.createdAt)).sortBy(/*name*/_._2)

    db.run(query.result).map(rows => rows.map { case (id, name, createdAt) => (id.toString, name) })
  }

  /** Count all partners. */
  def count(): Future[Int] = {
    // this should be changed to
    // db.run(computers.length.result)
    // when https://github.com/slick/slick/issues/1237 is fixed
    db.run(roles.map(_.id).length.result)
  }

  /** Insert a new company */
  def insert(role: Role): Future[Unit] =
    db.run(roles += role).map(_ => ())

  /** Insert new companies */
  def insert(roles: Seq[Role]): Future[Unit] =
    db.run(this.roles ++= roles).map(_ => ())
}