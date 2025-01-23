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

package uk.gov.hmrc.apiplatform.modules.organisations.domain.models

import cats.data.Validated._
import cats.data._
import cats.syntax.all._

import play.api.libs.json.{Format, Json}

case class ValidatedOrganisationName(value: String) extends AnyVal {
  override def toString(): String = value
}

object ValidatedOrganisationName {
  type ValidationResult[A] = ValidatedNec[OrganisationNameValidationFailed, A]

  private val minimumLength        = 2
  private val maximumLength        = 50
  private val disallowedCharacters = """<>/\"'`"""

  private def validateCharacters(applicationName: String): ValidationResult[String] = Validated.condNec(
    !applicationName.toCharArray.exists(c => c < 32 || c > 126 || disallowedCharacters.contains(c)),
    applicationName,
    OrganisationNameInvalidCharacters
  )

  private def validateLength(applicationName: String): ValidationResult[String] =
    Validated.condNec(
      applicationName.length >= minimumLength && applicationName.length <= maximumLength,
      applicationName,
      OrganisationNameInvalidLength
    )

  def apply(raw: String): Option[ValidatedOrganisationName] =
    validate(raw) match {
      case Valid(organisationName) => Some(organisationName)
      case _                       => None
    }

  def unsafeApply(raw: String): ValidatedOrganisationName =
    validate(raw).getOrElse(throw new RuntimeException(s"$raw is not a valid OrganisationName"))

  def validate(organisationName: String): ValidationResult[ValidatedOrganisationName] = {
    (validateCharacters(organisationName), validateLength(organisationName)).mapN((_, _) => new ValidatedOrganisationName(organisationName))
  }

  implicit val format: Format[ValidatedOrganisationName] = Json.valueFormat[ValidatedOrganisationName]
}

final case class OrganisationName(value: String) extends AnyVal {
  override def toString: String = value
}

object OrganisationName {
  def apply(value: String): OrganisationName = new OrganisationName(value.trim())

  implicit val orgNameFormat: Format[OrganisationName] = Json.valueFormat[OrganisationName]
}

trait OrganisationNameValidationFailed

case object OrganisationNameInvalidLength     extends OrganisationNameValidationFailed
case object OrganisationNameInvalidCharacters extends OrganisationNameValidationFailed
