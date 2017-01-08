package persistence

import javax.inject.{Inject, Singleton}
import domain.{User,DBUser, Role, Page}
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import slick.driver.JdbcProfile
import java.sql.Timestamp
import com.mohiva.play.silhouette.api.LoginInfo
import scala.concurrent.Future
import java.util.UUID;

/**
  * Created by Nuno on 16-12-2016.
  */
trait UserAuthComponent {

  /**
    * Finds a user by its signIn info.
    *
    * @param loginInfo The signIn info of the user to find.
    * @return The found user or None if no user for the given signIn info could be found.
    */
  def find(loginInfo: LoginInfo): Future[Option[User]]


  /**
    * Finds a user by its user ID.
    *
    * @param userID The ID of the user to find.
    * @return The found user or None if no user for the given ID could be found.
    */
  def find(userID: UUID): Future[Option[User]]

  /**
    * Saves a user.
    *
    * @param user The user to save.
    * @return The saved user.
    */
  def save(user: User): Future[User]

}


trait UserComponent {
  self: HasDatabaseConfigProvider[JdbcProfile] =>
  import driver.api._

  class Users(tag: Tag) extends Table[DBUser](tag, "USER") {

    //implicit val dateColumnType = MappedColumnType.base[Date, Long](d => d.getTime, d => new Date(d))

    def id = column[String]("ID", O.PrimaryKey)
    def firstName = column[Option[String]]("FIRSTNAME")
    def lastName = column[Option[String]]("LASTNAME")
    def fullName = column[Option[String]]("FULLNAME")
    def email = column[Option[String]]("EMAIL")
    def avatarURL = column[Option[String]]("AVATARURL")
    def activated = column[Boolean]("ACTIVATED")
    def roleId = column[Option[Long]]("ROLE_ID")
    def createdAt = column[Timestamp]("CREATEDAT")

    def * = (id, firstName, lastName,fullName, email,avatarURL,activated,roleId,createdAt) <> (DBUser.tupled, DBUser.unapply _ )
  }

}

@Singleton()
class UsersRepository @Inject() (protected val dbConfigProvider: DatabaseConfigProvider) extends RolesComponent
  with UserComponent with UserAuthComponent with AuthenticationComponent with HasDatabaseConfigProvider[JdbcProfile] {
  import driver.api._


  private val roles = TableQuery[Roles]
  private val users = TableQuery[Users]

  /** Retrieve a user from the id. */
  def findById(id: String): Future[Option[DBUser]] =
    db.run(users.filter(_.id === id).result.headOption)

  /** Retrieve a user from the email. */
  def findByEmail(email: String): Future[Option[DBUser]] =
    db.run(users.filter(_.email === email).result.headOption)

  /** Count all users. */
  def count(): Future[Int] = {
    // this should be changed to
    // db.run(computers.length.result)
    // when https://github.com/slick/slick/issues/1237 is fixed
    db.run(users.map(_.id).length.result)
  }
  /** Count users with a filter. */
  def count(filter: String): Future[Int] = {
    db.run(users.filter { user => user.firstName.toLowerCase like filter.toLowerCase }.length.result)
  }

  /** Return a page of (User,Role) */
  def list(page: Int = 0, pageSize: Int = 10, orderBy: Int = 1, filter: String = "%"): Future[Page[(DBUser, Role)]] = {

    val offset = pageSize * page
    val query =
      (for {
        (user, role) <- users joinLeft roles on (_.roleId === _.id)
        if user.firstName.toLowerCase like filter.toLowerCase
      } yield (user, role.map(_.id), role.map(_.name)))
        .drop(offset)
        .take(pageSize)

    for {
      totalRows <- count(filter)
      list = query.result.map { rows => rows.collect { case (user, id, Some(firstName)) => (user, Role(id, firstName,null)) } }
      result <- db.run(list)
    } yield Page(result, page, offset, totalRows)
  }

  /** Insert a new user. */
  def insert(user: User): Future[Unit] = {
    val userToInsert = DBUser(user.userID.toString, user.firstName, user.lastName, user.fullName, user.email, user.avatarURL,true, user.roleId, user.createdAt)
    db.run(users += userToInsert).map(_ => ())
  }

  /** Update a user.*/
  def update(id: String, user: User): Future[Unit] = {
    val userToUpdate = domain.DBUser(user.userID.toString,user.firstName,user.lastName,user.fullName,user.email,user.avatarURL,true,user.roleId,user.createdAt)
    db.run(users.filter(_.id === id).update(userToUpdate)).map(_ => ())
  }

  /** Update. */
  def updateUserDB(id: String, user: DBUser): Future[Unit] = {
    val userToUpdate: DBUser = user.copy(id)
    db.run(users.filter(_.id === id).update(userToUpdate)).map(_ => ())
  }


  /** Delete a user. */
  def delete(id: UUID): Future[Unit] =
    db.run(users.filter(_.id === id.toString).delete).map(_ => ())

  /**
    * Finds a user by its signIn info.
    *
    * @param loginInfo The signIn info of the user to find.
    * @return The found user or None if no user for the given signIn info could be found.
    */
  override def find(loginInfo: LoginInfo): Future[Option[User]] = {
    val userQuery = for {
      dbLoginInfo <- loginInfoQuery(loginInfo)
      dbUserLoginInfo <- UserLoginInfos.filter(_.loginInfoId === dbLoginInfo.id)
      dbUser <- users.filter(_.id === dbUserLoginInfo.userID)
    } yield dbUser

    db.run(userQuery.result.headOption).map { dbUserOption =>
      dbUserOption.map { user =>
        User(UUID.fromString(user.id), loginInfo, user.firstName, user.lastName,user.fullName, user.email, user.avatarURL,user.activated,user.roleId,user.createdAt)
      }
    }
  }

  /**
    * Finds a user by its user ID.
    *
    * @param userID The ID of the user to find.
    * @return The found user or None if no user for the given ID could be found.
    */
  override def find(userID: UUID): Future[Option[User]] = {
    val query = for {
      dbUser <- users.filter(_.id === userID.toString)
      dbUserLoginInfo <- UserLoginInfos.filter(_.userID === dbUser.id)
      dbLoginInfo <- LoginInfos.filter(_.id === dbUserLoginInfo.loginInfoId)
    } yield (dbUser, dbLoginInfo)

    db.run(query.result.headOption).map { resultOption =>
      resultOption.map {
        case (user, loginInfo) =>
          User(
            UUID.fromString(user.id),
            LoginInfo(loginInfo.providerID, loginInfo.providerKey),
            user.firstName,
            user.lastName,
            user.fullName,
            user.email,
            user.avatarURL,
            user.activated,
            user.roleId,
            user.createdAt
          )
      }
    }
  }

  /**
    * Saves a user.
    *
    * @param user The user to save.
    * @return The saved user.
    */
  override def save(user: User): Future[User] = {
    val dbUser = DBUser(user.userID.toString,
                        user.firstName,
                        user.lastName,
                        user.fullName,
                        user.email,
                        user.avatarURL,
                        user.activated,
                        user.roleId,
                        user.createdAt)
    val dbLoginInfo = domain.LoginInfo(None, user.loginInfo.providerID, user.loginInfo.providerKey)

    val loginInfoAction = {
      val retrieveLoginInfo = LoginInfos.filter(
        info => info.providerID === user.loginInfo.providerID &&
          info.providerKey === user.loginInfo.providerKey).result.headOption
      val insertLoginInfo = LoginInfos.returning(LoginInfos.map(_.id)).
        into((info, id) => info.copy(id = Some(id))) += dbLoginInfo
      for {
        loginInfoOption <- retrieveLoginInfo
        loginInfo <- loginInfoOption.map(DBIO.successful(_)).getOrElse(insertLoginInfo)
      } yield loginInfo
    }

    // combine database actions to be run sequentially
    val actions = (for {
      _ <- users.insertOrUpdate(dbUser)
      loginInfo <- loginInfoAction
      _ <- UserLoginInfos += domain.UserLoginInfo(dbUser.id, loginInfo.id.get)
    } yield ()).transactionally
    // run actions and return user afterwards
    db.run(actions).map(_ => user)
  }


  def getUsersContactInfo(customer:String,professional:String) : Future[Seq[(Option[String],Option[String])]] = {
    val query = for{
      user <- this.users if user.id === customer || user.id === professional
    } yield (user.email,user.fullName)

    db.run(query.result)
  }

}