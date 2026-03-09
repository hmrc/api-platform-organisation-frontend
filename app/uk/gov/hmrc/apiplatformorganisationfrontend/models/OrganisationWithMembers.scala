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

package uk.gov.hmrc.apiplatformorganisationfrontend.models

import uk.gov.hmrc.apiplatform.modules.organisations.domain.models.{Collaborator, Organisation}
import uk.gov.hmrc.apiplatform.modules.tpd.core.dto.RegisteredOrUnregisteredUser

case class CollaboratorWithUserDetails(collaborator: Collaborator, user: RegisteredOrUnregisteredUser)

object CollaboratorWithUserDetails {

  def apply(collaborator: Collaborator, user: Option[RegisteredOrUnregisteredUser]): Option[CollaboratorWithUserDetails] = {
    user match {
      case Some(u) => Some(CollaboratorWithUserDetails(collaborator, u))
      case _       => None
    }
  }
}

case class OrganisationWithAllMembersDetails(organisation: Organisation, collaborators: Set[CollaboratorWithUserDetails])

object OrganisationWithAllMembersDetails {

  def apply(organisation: Organisation, members: List[RegisteredOrUnregisteredUser]): OrganisationWithAllMembersDetails = {
    val collaborators = organisation.collaborators.map(c => CollaboratorWithUserDetails.apply(c, members.find(u => u.userId == c.userId))).flatten
    OrganisationWithAllMembersDetails(organisation, collaborators)
  }
}

case class OrganisationWithMemberDetails(organisation: Organisation, collaborator: CollaboratorWithUserDetails)

object OrganisationWithMemberDetails {

  def apply(organisation: Organisation, collaborator: Collaborator, member: RegisteredOrUnregisteredUser): OrganisationWithMemberDetails = {
    OrganisationWithMemberDetails(organisation, CollaboratorWithUserDetails(collaborator, member))
  }
}
