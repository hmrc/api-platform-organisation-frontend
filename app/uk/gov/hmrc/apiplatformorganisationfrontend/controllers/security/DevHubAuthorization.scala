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

package uk.gov.hmrc.apiplatformorganisationfrontend.controllers.security

import scala.concurrent.Future

import cats.data.OptionT

import play.api.mvc._
import uk.gov.hmrc.apiplatformorganisationfrontend.config.AppConfig
import uk.gov.hmrc.apiplatformorganisationfrontend.connectors.ThirdPartyDeveloperConnector
import uk.gov.hmrc.apiplatformorganisationfrontend.controllers.BaseController
import uk.gov.hmrc.apiplatformorganisationfrontend.controllers.models._
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendHeaderCarrierProvider

import uk.gov.hmrc.apiplatform.modules.tpd.session.domain.models.{LoggedInState, UserSession, UserSessionId}

trait DevHubAuthorization extends FrontendHeaderCarrierProvider with CookieEncoding {
  self: BaseController =>

  val thirdPartyDeveloperConnector: ThirdPartyDeveloperConnector

  implicit val appConfig: AppConfig

  object DeveloperSessionFilter {
    type Type = UserSession => Boolean

    val onlyTrueIfLoggedInFilter: DeveloperSessionFilter.Type = _.loggedInState == LoggedInState.LOGGED_IN
  }

  def loggedInActionRefiner(filter: DeveloperSessionFilter.Type = DeveloperSessionFilter.onlyTrueIfLoggedInFilter): ActionRefiner[MessagesRequest, UserRequest] =
    new ActionRefiner[MessagesRequest, UserRequest] {
      def executionContext = ec

      def refine[A](msgRequest: MessagesRequest[A]): Future[Either[Result, UserRequest[A]]] = {
        lazy val loginRedirect = Redirect(s"${appConfig.thirdPartyDeveloperFrontendUrl}/developer/login")

        implicit val request = msgRequest

        OptionT(loadSession)
          .filter(filter)
          .toRight(loginRedirect)
          .map(new UserRequest(_, msgRequest))
          .value
      }
    }

  def loggedInAction(block: UserRequest[AnyContent] => Future[Result]): Action[AnyContent] =
    Action.async { implicit request =>
      loggedInActionRefiner().invokeBlock(request, block)
    }

  private[security] def loadSession[A](implicit request: Request[A]): Future[Option[UserSession]] = {
    (for {
      sessionId <- extractUserSessionIdFromCookie(request)
    } yield fetchDeveloperSession(sessionId))
      .getOrElse(Future.successful(None))
  }

  private def fetchDeveloperSession[A](sessionId: UserSessionId)(implicit hc: HeaderCarrier): Future[Option[UserSession]] = {
    thirdPartyDeveloperConnector.fetchSession(sessionId)
  }
}
