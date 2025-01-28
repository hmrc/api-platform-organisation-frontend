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

import play.api.libs.json._

import uk.gov.hmrc.apiplatform.modules.common.utils.BaseJsonFormattersSpec
import uk.gov.hmrc.apiplatform.modules.organisations.submissions.utils.SubmissionsTestData

class QuestionSpec extends BaseJsonFormattersSpec with SubmissionsTestData {

  def jsonTextQuestion(mark: String = "fail") = {
    s"""{
       |  "id" : "b2dbf6a1-e39b-4c38-a524-19f0854ca1cc",
       |  "wording" : "What is your organisation’s website address?",
       |  "hintText" : {
       |    "text" : "For example https://example.com",
       |    "statementType" : "text"
       |  },
       |  "validation" : {
       |    "validationType" : "url"
       |  },
       |  "absence" : [ "My organisation doesn't have a website", "$mark" ],
       |  "errorInfo" : {
       |    "summary" : "Enter a website address in the correct format, like https://example.com",
       |    "message" : "Enter a URL in the correct format, like https://example.com"
       |  }
       |}""".stripMargin
  }

  val jsonYesNoQuestion =
    """{
      |  "id" : "99d9362d-e365-4af1-aa46-88e95f9858f7",
      |  "wording" : "Are you the individual responsible for the software in your organisation?",
      |  "statement" : {
      |    "fragments" : [ {
      |      "text" : "As the responsible individual you:",
      |      "statementType" : "text"
      |    }, {
      |      "bullets" : [ {
      |        "fragments" : [ {
      |          "text" : "ensure your software conforms to the ",
      |          "statementType" : "text"
      |        }, {
      |          "text" : "terms of use (opens in new tab)",
      |          "url" : "/api-documentation/docs/terms-of-use",
      |          "statementType" : "link"
      |        } ],
      |        "statementType" : "compound"
      |      }, {
      |        "fragments" : [ {
      |          "text" : "understand the ",
      |          "statementType" : "text"
      |        }, {
      |          "text" : "consequences of not conforming to the terms of use (opens in new tab)",
      |          "url" : "/api-documentation/docs/terms-of-use",
      |          "statementType" : "link"
      |        } ],
      |        "statementType" : "compound"
      |      } ],
      |      "statementType" : "bullets"
      |    } ]
      |  },
      |  "yesMarking" : "pass",
      |  "noMarking" : "pass",
      |  "errorInfo" : {
      |    "summary" : "Select Yes if you are the individual responsible for the software in your organisation"
      |  }
      |}""".stripMargin

  val jsonChooseOneOfQuestion =
    """{
      |  "id" : "cbdf264f-be39-4638-92ff-6ecd2259c662",
      |  "wording" : "What is your organisation type?",
      |  "marking" : [ {
      |    "UK limited company" : "pass"
      |  }, {
      |    "Sole trader" : "pass"
      |  }, {
      |    "Partnership" : "pass"
      |  }, {
      |    "Registered society" : "pass"
      |  }, {
      |    "Charitable Incorporated Organisation (CIO)" : "pass"
      |  }, {
      |    "Trust" : "pass"
      |  }, {
      |    "Non-UK company with a branch or place of business in the UK" : "warn"
      |  }, {
      |    "Non-UK company without a branch or place of business in the UK" : "fail"
      |  } ],
      |  "errorInfo" : {
      |    "summary" : "Select your organisation type"
      |  }
      |}""".stripMargin

  "question absence text" in {
    OrganisationDetails.question2c.absenceText shouldBe None
    ResponsibleIndividualDetails.question6.absenceText shouldBe Some("My organisation doesn't have a website")
  }

  "question absence mark" in {
    OrganisationDetails.question2c.absenceMark shouldBe None
    ResponsibleIndividualDetails.question6.absenceMark shouldBe Some(Mark.Fail)
  }

  "question is optional" in {
    OrganisationDetails.question1.isOptional shouldBe false
    OrganisationDetails.question2a.isOptional shouldBe true
  }

  "question html value" in {
    OrganisationDetails.question1.choices.head.htmlValue shouldBe "UK-limited-company"
  }

  "toJson for text question" in {
    Json.prettyPrint(Json.toJson(ResponsibleIndividualDetails.question6)) shouldBe jsonTextQuestion()
  }

  "read text question from json" in {
    testFromJson[Question.TextQuestion](jsonTextQuestion())(ResponsibleIndividualDetails.question6)
  }

  "read invalid text question from json" in {
    intercept[Exception] {
      testFromJson[Question.TextQuestion](jsonTextQuestion("invalid"))(ResponsibleIndividualDetails.question6)
    }
  }

  "toJson for yesno question" in {
    Json.prettyPrint(Json.toJson(ResponsibleIndividualDetails.question1)) shouldBe jsonYesNoQuestion
  }

  "read yes no question from json" in {
    testFromJson[Question.YesNoQuestion](jsonYesNoQuestion)(ResponsibleIndividualDetails.question1)
  }

  "toJson for choose one of question" in {
    Json.prettyPrint(Json.toJson(OrganisationDetails.question1)) shouldBe jsonChooseOneOfQuestion
  }

  "read choose one of question from json" in {
    testFromJson[Question.ChooseOneOfQuestion](jsonChooseOneOfQuestion)(OrganisationDetails.question1)
  }
}
