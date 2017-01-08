package framework.modules

import framework.services.{AuthTokenService, AuthTokenServiceImpl}
import net.codingwell.scalaguice.ScalaModule
import com.google.inject.AbstractModule
import persistence.{AuthTokenRepository, AuthTokenRepositoryImpl}

/**
  * Created by Nuno on 20-12-2016.
  */
class BaseModule extends AbstractModule with ScalaModule {

  /**
    * Configures the module.
    */
  def configure(): Unit = {
    bind[AuthTokenRepository].to[AuthTokenRepositoryImpl]
    bind[AuthTokenService].to[AuthTokenServiceImpl]
  }
}