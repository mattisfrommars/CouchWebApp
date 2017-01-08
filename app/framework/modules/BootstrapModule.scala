package framework.modules

import bootstrap.InitialData
import com.google.inject.AbstractModule

/**
  * Created by Nuno on 23-12-2016.
  */
class BootstrapModule extends AbstractModule{
  override def configure(): Unit = {
    bind(classOf[InitialData]).asEagerSingleton()
  }
}
