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

package uk.gov.hmrc.apiplatformorganisationfrontend.services

import scala.concurrent.Future.successful

import uk.gov.hmrc.http.HeaderCarrier

import uk.gov.hmrc.apiplatform.modules.common.domain.models._
import uk.gov.hmrc.apiplatform.modules.common.utils.FixedClock
import uk.gov.hmrc.apiplatform.modules.organisations.submissions.domain.models.{Question, SubmissionId}
import uk.gov.hmrc.apiplatform.modules.organisations.submissions.utils.SubmissionsTestData
import uk.gov.hmrc.apiplatformorganisationfrontend.AsyncHmrcSpec
import uk.gov.hmrc.apiplatformorganisationfrontend.connectors.OrganisationConnector

class SubmissionServiceSpec extends AsyncHmrcSpec {

  trait Setup extends FixedClock with SubmissionsTestData {
    implicit val hc: HeaderCarrier = HeaderCarrier()

    val mockOrganisationConnector = mock[OrganisationConnector]

    val underTest = new SubmissionService(
      mockOrganisationConnector
    )
  }

  "fetch" should {
    "return extended submission for given submission id" in new Setup {
      when(mockOrganisationConnector.fetchSubmission(*[SubmissionId])(*)).thenReturn(successful(Some(completelyAnswerExtendedSubmission)))

      val result = await(underTest.fetch(completelyAnswerExtendedSubmission.submission.id))

      result shouldBe defined
      result.get.submission.id shouldBe completelyAnswerExtendedSubmission.submission.id
    }

    "return latest submission for given application id" in new Setup {
      when(mockOrganisationConnector.fetchLatestSubmissionByUserId(*[UserId])(*)).thenReturn(successful(Some(aSubmission)))

      val result = await(underTest.fetchLatestSubmissionByUserId(aSubmission.startedBy))

      result shouldBe defined
      result.get.id shouldBe aSubmission.id
    }

    "return latest extended submission for given application id" in new Setup {
      when(mockOrganisationConnector.fetchLatestExtendedSubmissionByUserId(*[UserId])(*)).thenReturn(successful(Some(completelyAnswerExtendedSubmission)))

      val result = await(underTest.fetchLatestExtendedSubmissionByUserId(completelyAnswerExtendedSubmission.submission.startedBy))

      result shouldBe defined
      result.get.submission.id shouldBe completelyAnswerExtendedSubmission.submission.id
    }
  }

  "recordAnswer" should {
    "record answer for given submisson id and question id" in new Setup {
      when(mockOrganisationConnector.recordAnswer(*[SubmissionId], *[Question.Id], *)(*)).thenReturn(successful(Right(answeringSubmission.withIncompleteProgress())))

      val result = await(underTest.recordAnswer(completelyAnswerExtendedSubmission.submission.id, questionId, List("")))

      result.isRight shouldBe true
    }
  }

  "createSubmission" should {
    "create submisson" in new Setup {
      when(mockOrganisationConnector.createSubmission(*[UserId], *[LaxEmailAddress])(*)).thenReturn(successful(Some(submittedSubmission)))

      val result = await(underTest.createSubmission(userId, LaxEmailAddress("bob@example.com")))

      result.isDefined shouldBe true
    }
  }

  "submitSubmission" should {
    "submit submisson" in new Setup {
      when(mockOrganisationConnector.submitSubmission(*[SubmissionId], *[LaxEmailAddress])(*)).thenReturn(successful(Right(submittedSubmission)))

      val result = await(underTest.submitSubmission(submittedSubmission.id, LaxEmailAddress("bob@example.com")))

      result.isRight shouldBe true
    }
  }
}
