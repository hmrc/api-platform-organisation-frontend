/*
 * Copyright 2023 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.apiplatformorganisationfrontend.controllers

import scala.concurrent.ExecutionContext

import play.api.mvc.{MessagesControllerComponents, Result}
import uk.gov.hmrc.play.bootstrap.controller.WithUnsafeDefaultFormBinding
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController

import uk.gov.hmrc.apiplatform.modules.common.services.EitherTHelper
import uk.gov.hmrc.apiplatformorganisationfrontend.config.{AppConfig, ErrorHandler}
import uk.gov.hmrc.apiplatformorganisationfrontend.controllers.security.DevHubAuthorization

abstract class BaseController(mcc: MessagesControllerComponents) extends FrontendController(mcc) with DevHubAuthorization with WithUnsafeDefaultFormBinding {
  val errorHandler: ErrorHandler

  implicit def ec: ExecutionContext

  implicit val appConfig: AppConfig

  protected lazy val ETR = EitherTHelper.make[Result](ec)
}
