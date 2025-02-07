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

import cats.data.Validated.{Invalid, Valid}

import play.api.libs.json.{JsString, Json}

import uk.gov.hmrc.apiplatform.modules.common.utils.BaseJsonFormattersSpec

class OrganisationNumberSpec extends BaseJsonFormattersSpec {

  "OrganisationNumber" should {
    "toString" in {
      OrganisationNumber("Ab123456").toString() shouldBe "Ab123456"
    }

    "convert to json" in {
      Json.toJson[OrganisationNumber](OrganisationNumber("Ab123456")) shouldBe JsString("Ab123456")
    }

    "read from json" in {
      testFromJson[OrganisationNumber](JsString("Ab123456").toString)(OrganisationNumber("Ab123456"))
    }
  }

  "ValidatedOrganisationNumber" should {
    "convert to json" in {
      Json.toJson[ValidatedOrganisationNumber](ValidatedOrganisationNumber.unsafeApply("Ab123456")) shouldBe JsString("Ab123456")
    }

    "read from json" in {
      testFromJson[ValidatedOrganisationNumber](JsString("Ab123456").toString)(ValidatedOrganisationNumber.unsafeApply("Ab123456"))
    }
  }

  "validate a good organisation number" in {
    ValidatedOrganisationNumber.validate("Ab123456") shouldBe Valid(ValidatedOrganisationNumber.unsafeApply("Ab123456"))
  }

  "check validity of a good organisation number" in {
    ValidatedOrganisationNumber.validate("Ab123456").isValid shouldBe true
    ValidatedOrganisationNumber("Ab123456").isDefined shouldBe true
  }

  "check unsafeApply with a good organisation number " in {
    ValidatedOrganisationNumber.unsafeApply("Ab123456") shouldBe ValidatedOrganisationNumber.unsafeApply(
      "Ab123456"
    )
  }

  "check unsafeApply with a bad organisation number " in {
    val ex = intercept[RuntimeException] {
      ValidatedOrganisationNumber.unsafeApply("M")
    }
    ex.getMessage shouldBe "M is not a valid OrganisationNumber"
  }

  "invalidate an organisation number with too few characters" in {
    ValidatedOrganisationNumber("M").isDefined shouldBe false
    ValidatedOrganisationNumber.validate("M") match {
      case Invalid(x) =>
        x.length shouldBe 1
        x.head shouldBe OrganisationNumberInvalidLength
      case _          => fail("should be invalid")
    }
  }

  "invalidate an organisation number with too many characters" in {
    ValidatedOrganisationNumber("123456701").isDefined shouldBe false
    ValidatedOrganisationNumber.validate("123456701") match {
      case Invalid(x) =>
        x.length shouldBe 1
        x.head shouldBe OrganisationNumberInvalidLength
      case _          => fail("should be invalid")
    }
  }

  "invalidate organisation numbers with non-ASCII characters" in {
    ValidatedOrganisationNumber("£1234567").isDefined shouldBe false
    ValidatedOrganisationNumber.validate("£1234567") match {
      case Invalid(x) =>
        x.length shouldBe 1
        x.head shouldBe OrganisationNumberInvalidCharacters
      case _          => fail("should be invalid")
    }
  }

  "invalidate organisation numbers with all validations" in {
    ValidatedOrganisationNumber("£").isDefined shouldBe false
    ValidatedOrganisationNumber.validate("£") match {
      case Invalid(x) =>
        x.length shouldBe 2
        x.head shouldBe OrganisationNumberInvalidCharacters
        x.toNonEmptyList.tail.head shouldBe OrganisationNumberInvalidLength
      case _          => fail("should be invalid")
    }
  }
}
