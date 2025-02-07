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

package uk.gov.hmrc.apiplatform.modules.organisations.submissions.domain.models

import cats.data.Validated._
import cats.data._
import cats.syntax.all._

import play.api.libs.json.{Format, Json}

case class ValidatedOrganisationNumber(value: String) extends AnyVal {
  override def toString(): String = value
}

object ValidatedOrganisationNumber {
  type ValidationResult[A] = ValidatedNec[OrganisationNumberValidationFailed, A]

  private val requiredLength = 8
  private val allowedRegex   = "^[a-zA-Z0-9]*$".r

  private def validateCharacters(applicationNumber: String): ValidationResult[String] = Validated.condNec(
    allowedRegex.matches(applicationNumber),
    applicationNumber,
    OrganisationNumberInvalidCharacters
  )

  private def validateLength(applicationNumber: String): ValidationResult[String] =
    Validated.condNec(
      applicationNumber.length == requiredLength,
      applicationNumber,
      OrganisationNumberInvalidLength
    )

  def apply(raw: String): Option[ValidatedOrganisationNumber] =
    validate(raw) match {
      case Valid(organisationNumber) => Some(organisationNumber)
      case _                         => None
    }

  def unsafeApply(raw: String): ValidatedOrganisationNumber =
    validate(raw).getOrElse(throw new RuntimeException(s"$raw is not a valid OrganisationNumber"))

  def validate(organisationNumber: String): ValidationResult[ValidatedOrganisationNumber] = {
    (validateCharacters(organisationNumber), validateLength(organisationNumber)).mapN((_, _) => new ValidatedOrganisationNumber(organisationNumber))
  }

  implicit val format: Format[ValidatedOrganisationNumber] = Json.valueFormat[ValidatedOrganisationNumber]
}

final case class OrganisationNumber(value: String) extends AnyVal {
  override def toString: String = value
}

object OrganisationNumber {
  def apply(value: String): OrganisationNumber = new OrganisationNumber(value.trim())

  implicit val orgNumberFormat: Format[OrganisationNumber] = Json.valueFormat[OrganisationNumber]
}

trait OrganisationNumberValidationFailed

case object OrganisationNumberInvalidLength     extends OrganisationNumberValidationFailed
case object OrganisationNumberInvalidCharacters extends OrganisationNumberValidationFailed
