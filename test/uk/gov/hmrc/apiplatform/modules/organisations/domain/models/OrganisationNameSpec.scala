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

package uk.gov.hmrc.apiplatform.modules.organisations.domain.models

import cats.data.Validated.{Invalid, Valid}

import play.api.libs.json.{JsString, Json}

import uk.gov.hmrc.apiplatform.modules.common.utils.BaseJsonFormattersSpec

class OrganisationNameSpec extends BaseJsonFormattersSpec {

  "OrganisationName" should {
    "toString" in {
      OrganisationName("My Org").toString() shouldBe "My Org"
    }

    "convert to json" in {
      Json.toJson[OrganisationName](OrganisationName("My Org")) shouldBe JsString("My Org")
    }

    "read from json" in {
      testFromJson[OrganisationName](JsString("My Org").toString)(OrganisationName("My Org"))
    }
  }

  "ValidatedOrganisationName" should {
    "convert to json" in {
      Json.toJson[ValidatedOrganisationName](ValidatedOrganisationName.unsafeApply("My Org")) shouldBe JsString("My Org")
    }

    "read from json" in {
      testFromJson[ValidatedOrganisationName](JsString("My Org").toString)(ValidatedOrganisationName.unsafeApply("My Org"))
    }

    "validate a good organisation name" in {
      ValidatedOrganisationName.validate("My Org") shouldBe Valid(ValidatedOrganisationName.unsafeApply("My Org"))
    }

    "check validity of a good organisation name" in {
      ValidatedOrganisationName.validate("My org-restricted, special org worth $100 in BRASS").isValid shouldBe true
      ValidatedOrganisationName("My org-restricted, special org worth $100 in BRASS").isDefined shouldBe true
    }

    "check unsafeApply with a good organisation name " in {
      ValidatedOrganisationName.unsafeApply("My org-restricted, special org worth $100 in BRASS") shouldBe ValidatedOrganisationName.unsafeApply(
        "My org-restricted, special org worth $100 in BRASS"
      )
    }

    "check unsafeApply with a bad organisation name " in {
      val ex = intercept[RuntimeException] {
        ValidatedOrganisationName.unsafeApply("M")
      }
      ex.getMessage shouldBe "M is not a valid OrganisationName"
    }

    "invalidate an organisation name with too few characters" in {
      ValidatedOrganisationName("M").isDefined shouldBe false
      ValidatedOrganisationName.validate("M") match {
        case Invalid(x) =>
          x.length shouldBe 1
          x.head shouldBe OrganisationNameInvalidLength
        case _          => fail("should be invalid")
      }
    }

    "invalidate an organisation name with too many characters" in {
      ValidatedOrganisationName("My org-restricted, special org worth $100 in SILVER").isDefined shouldBe false
      ValidatedOrganisationName.validate("My org-restricted, special org worth $100 in SILVER") match {
        case Invalid(x) =>
          x.length shouldBe 1
          x.head shouldBe OrganisationNameInvalidLength
        case _          => fail("should be invalid")
      }
    }

    "invalidate organisation names with non-ASCII characters" in {
      ValidatedOrganisationName("£50").isDefined shouldBe false
      ValidatedOrganisationName.validate("£50") match {
        case Invalid(x) =>
          x.length shouldBe 1
          x.head shouldBe OrganisationNameInvalidCharacters
        case _          => fail("should be invalid")
      }
    }

    "invalidate organisation names with all validations" in {
      ValidatedOrganisationName("ɐ").isDefined shouldBe false
      ValidatedOrganisationName.validate("ɐ") match {
        case Invalid(x) =>
          x.length shouldBe 2
          x.head shouldBe OrganisationNameInvalidCharacters
          x.toNonEmptyList.tail.head shouldBe OrganisationNameInvalidLength
        case _          => fail("should be invalid")
      }
    }

    "invalidate organisation names with disallowed special characters" in {
      List('<', '>', '/', '\\', '"', '\'', '`').foreach(c => {
        ValidatedOrganisationName.validate(s"invalid $c") match {
          case Invalid(x) =>
            x.length shouldBe 1
            x.head shouldBe OrganisationNameInvalidCharacters
          case _          => fail("should be invalid")
        }
      })
    }
  }
}
