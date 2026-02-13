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

package uk.gov.hmrc.apiplatformorganisationfrontend.controllers.models

import play.api.mvc._

import uk.gov.hmrc.apiplatform.modules.common.domain.models.{LaxEmailAddress, UserId}
import uk.gov.hmrc.apiplatform.modules.organisations.domain.models.{Member, Organisation}
import uk.gov.hmrc.apiplatform.modules.tpd.core.domain.models.User
import uk.gov.hmrc.apiplatform.modules.tpd.session.domain.models.{LoggedInState, UserSession}

class UserRequest[A](val userSession: UserSession, val msgRequest: MessagesRequest[A]) extends MessagesRequest[A](msgRequest, msgRequest.messagesApi) {
  lazy val sessionId = userSession.sessionId

  lazy val developer: User              = userSession.developer
  lazy val userId: UserId               = developer.userId
  lazy val email: LaxEmailAddress       = developer.email
  lazy val loggedInState: LoggedInState = userSession.loggedInState

  lazy val displayedName: String = developer.displayedName

  lazy val loggedInName: Option[String] =
    if (loggedInState.isLoggedIn) {
      Some(displayedName)
    } else {
      None
    }
}

class MaybeUserRequest[A](val userSession: Option[UserSession], request: MessagesRequest[A]) extends MessagesRequest[A](request, request.messagesApi)

trait HasOrganisation {
  def organisation: Organisation
}

class OrganisationRequest[A](
    val organisation: Organisation,
    val member: Member,
    val userRequest: UserRequest[A]
  ) extends UserRequest[A](userRequest.userSession, userRequest.msgRequest) with HasOrganisation {}
