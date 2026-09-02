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

import scala.concurrent.ExecutionContext
import scala.concurrent.Future.successful

import uk.gov.hmrc.http.HeaderCarrier

import uk.gov.hmrc.apiplatform.modules.common.domain.models.*
import uk.gov.hmrc.apiplatform.modules.common.utils.FixedClock
import uk.gov.hmrc.apiplatform.modules.organisations.domain.models.OrganisationName
import uk.gov.hmrc.apiplatform.modules.organisations.submissions.domain.models.{OrganisationAllowList, Question, SubmissionId}
import uk.gov.hmrc.apiplatform.modules.organisations.submissions.utils.SubmissionsTestData
import uk.gov.hmrc.apiplatform.modules.tpd.core.dto.UpdateRequest
import uk.gov.hmrc.apiplatform.modules.tpd.test.data.UserTestData
import uk.gov.hmrc.apiplatform.modules.tpd.test.utils.LocalUserIdTracker
import uk.gov.hmrc.apiplatformorganisationfrontend.AsyncHmrcSpec
import uk.gov.hmrc.apiplatformorganisationfrontend.connectors.{ApiPlatformDeskproConnector, OrganisationConnector, ThirdPartyDeveloperConnector}

class SubmissionServiceSpec extends AsyncHmrcSpec with LocalUserIdTracker with UserTestData {

  implicit val ec: ExecutionContext = ExecutionContext.global

  trait Setup extends FixedClock with SubmissionsTestData {
    implicit val hc: HeaderCarrier = HeaderCarrier()

    val mockOrganisationConnector        = mock[OrganisationConnector]
    val mockThirdPartyDeveloperConnector = mock[ThirdPartyDeveloperConnector]
    val mockApiPlatformDeskproConnector  = mock[ApiPlatformDeskproConnector]

    val underTest = new SubmissionService(
      mockOrganisationConnector,
      mockThirdPartyDeveloperConnector,
      mockApiPlatformDeskproConnector
    )

    val allowList = OrganisationAllowList(userId, OrganisationName("My Org 1"), "requestedBy", instant)
    val email     = LaxEmailAddress("bob@example.com")
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

      val result = await(underTest.recordAnswer(completelyAnswerExtendedSubmission.submission.id, questionId, Map("" -> Seq(""))))

      result.isRight shouldBe true
    }
  }

  "createSubmission" should {
    "create submisson" in new Setup {
      when(mockOrganisationConnector.createSubmission(*[UserId], *[LaxEmailAddress])(*)).thenReturn(successful(Some(submittedSubmission)))

      val result = await(underTest.createSubmission(userId, email))

      result.isDefined shouldBe true
    }
  }

  "submitSubmission" should {
    "submit submisson and update profile when RI name given" in new Setup {
      when(mockOrganisationConnector.submitSubmission(*[SubmissionId], *[LaxEmailAddress])(*)).thenReturn(successful(Right(submittedSubmission)))
      when(mockThirdPartyDeveloperConnector.updateProfile(*[UserId], *)(*)).thenReturn(successful(standardDeveloper))

      val result = await(underTest.submitSubmission(submittedSubmission.id, userId, email, adminDeveloper))

      result.isRight shouldBe true
      verify(mockOrganisationConnector).submitSubmission(eqTo(submittedSubmission.id), eqTo(email))(*)
      verify(mockThirdPartyDeveloperConnector).updateProfile(eqTo(userId), eqTo(UpdateRequest("Bob", "Roberts")))(*)
    }

    "submit submisson and not update profile when RI name not given" in new Setup {
      val submissionWithNoRIName = aSubmission
        .hasCompletelyAnsweredWith(sampleAnswersToQuestions1)
        .withCompletedProgress()
        .submission

      when(mockOrganisationConnector.submitSubmission(*[SubmissionId], *[LaxEmailAddress])(*)).thenReturn(successful(Right(submissionWithNoRIName)))

      val result = await(underTest.submitSubmission(submissionWithNoRIName.id, userId, email, adminDeveloper))

      result.isRight shouldBe true
      verify(mockOrganisationConnector).submitSubmission(eqTo(submissionWithNoRIName.id), eqTo(email))(*)
      verify(mockThirdPartyDeveloperConnector, never).updateProfile(*[UserId], *)(*)
    }
  }

  "fetchAllowList" should {
    "fetch allow list" in new Setup {
      when(mockOrganisationConnector.fetchOrganisationAllowList(*[UserId])(*)).thenReturn(successful(Some(allowList)))

      val result = await(underTest.fetchAllowList(userId))

      result.isDefined shouldBe true
      result shouldBe Some(allowList)
    }
  }
}
