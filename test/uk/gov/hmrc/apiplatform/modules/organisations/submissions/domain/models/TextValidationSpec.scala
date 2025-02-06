/*
 * Copyright 2024 HM Revenue & Customs
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

import org.scalatest.prop.TableDrivenPropertyChecks

import play.api.libs.json.Json

import uk.gov.hmrc.apiplatform.modules.common.utils.HmrcSpec

class TextValidationSpec extends HmrcSpec with TableDrivenPropertyChecks {

  "TextValidation" should {

    val table = Table(
      ("Validation", "Expected Json"),
      (TextValidation.Url, Json.parse("""{"validationType":"url"}""")),
      (TextValidation.MatchRegex("abc"), Json.parse("""{"validationType":"regex","regex":"abc"}""")),
      (TextValidation.OrganisationName, Json.parse("""{"validationType":"organisationName"}""")),
      (TextValidation.Email, Json.parse("""{"validationType":"email"}"""))
    )

    "convert json to and from all types" in {
      forAll(table) {
        case (validation, json) =>
          val jsvalue = Json.toJson[TextValidation](validation)
          jsvalue shouldBe json
          Json.fromJson[TextValidation](jsvalue).get shouldBe validation
      }
    }

    "find a good email valid" in {
      TextValidation.Email.isValid("bob@exmaple.com") shouldBe true
    }

    "find bad emails invalid" in {
      TextValidation.Email.isValid("bob") shouldBe false
      TextValidation.Email.isValid("bob@fruity") shouldBe false
    }

    "find a good url" in {
      TextValidation.Url.isValid("https://bob.com") shouldBe true
      TextValidation.Url.isValid("http://bob.com") shouldBe true
      TextValidation.Url.isValid("http://bob") shouldBe true
    }

    "find a bad url" in {
      TextValidation.Url.isValid("bob") shouldBe false
      TextValidation.Url.isValid("bob://bob.com") shouldBe false
    }

    "find a good regex" in {
      TextValidation.MatchRegex("[0-9][0-9]").isValid("12") shouldBe true
      TextValidation.MatchRegex("[0-9][A-Z]").isValid("1A") shouldBe true
    }

    "find a bad regex" in {
      TextValidation.MatchRegex("[0-9][0-9]").isValid("1A") shouldBe false
      TextValidation.MatchRegex("[0-9][A-Z]").isValid("12") shouldBe false
    }

    "find a good organisationName name" in {
      TextValidation.OrganisationName.isValid("My Org") shouldBe true
      TextValidation.OrganisationName.isValid("1A$%^&") shouldBe true
    }

    "find a bad organisationName name" in {
      TextValidation.OrganisationName.isValid("1") shouldBe false
      TextValidation.OrganisationName.isValid("12£") shouldBe false
    }

    "find a valid organisationNumber" in {
      TextValidation.OrganisationNumber.isValid("1234aBcD") shouldBe true
      TextValidation.OrganisationNumber.isValid("AbCd1234") shouldBe true
    }

    "find a invalid organisationNumber" in {
      TextValidation.OrganisationNumber.isValid("12") shouldBe false
      TextValidation.OrganisationNumber.isValid("123456789") shouldBe false
      TextValidation.OrganisationNumber.isValid("AbCd12&£") shouldBe false
    }
  }
}
