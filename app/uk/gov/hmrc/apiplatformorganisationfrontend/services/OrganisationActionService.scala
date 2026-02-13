/*
 * Copyright 2025 HM Revenue & Customs
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

package uk.gov.hmrc.apiplatformorganisationfrontend.services

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

import cats.data.OptionT

import uk.gov.hmrc.http.HeaderCarrier

import uk.gov.hmrc.apiplatform.modules.common.domain.models.OrganisationId
import uk.gov.hmrc.apiplatform.modules.common.services.EitherTHelper
import uk.gov.hmrc.apiplatformorganisationfrontend.connectors.OrganisationConnector
import uk.gov.hmrc.apiplatformorganisationfrontend.controllers.models.{OrganisationRequest, UserRequest}

@Singleton
class OrganisationActionService @Inject() (
    organisationConnector: OrganisationConnector
  )(implicit val ec: ExecutionContext
  ) extends EitherTHelper[String] {

  def process[A](organisationId: OrganisationId, userRequest: UserRequest[A])(implicit hc: HeaderCarrier): Future[Option[OrganisationRequest[A]]] = {
    import cats.implicits._

    (
      for {
        organisation <- OptionT(organisationConnector.fetchOrganisation(organisationId))
        member       <- OptionT.fromOption[Future](organisation.members.find(_.userId == userRequest.developer.userId))
      } yield new OrganisationRequest(organisation, member, userRequest)
    )
      .value
  }
}
