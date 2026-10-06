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
import scala.concurrent.{ExecutionContext, Future}

import play.api.http.Status.*
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}

import uk.gov.hmrc.apiplatform.modules.common.domain.models.*
import uk.gov.hmrc.apiplatform.modules.common.utils.FixedClock
import uk.gov.hmrc.apiplatform.modules.organisations.domain.models.OrganisationName
import uk.gov.hmrc.apiplatform.modules.organisations.submissions.domain.models.*
import uk.gov.hmrc.apiplatform.modules.organisations.submissions.domain.services.{ValidationError, ValidationErrors}
import uk.gov.hmrc.apiplatform.modules.organisations.submissions.utils.SubmissionsTestData
import uk.gov.hmrc.apiplatform.modules.tpd.core.dto.UpdateRequest
import uk.gov.hmrc.apiplatform.modules.tpd.test.data.UserTestData
import uk.gov.hmrc.apiplatform.modules.tpd.test.utils.LocalUserIdTracker
import uk.gov.hmrc.apiplatformorganisationfrontend.AsyncHmrcSpec
import uk.gov.hmrc.apiplatformorganisationfrontend.connectors.ApiPlatformDeskproConnector.{CreateMessageFailure, CreateMessageSuccess, CreateTicketResponse}
import uk.gov.hmrc.apiplatformorganisationfrontend.connectors.{ApiPlatformDeskproConnector, OrganisationConnector, ThirdPartyDeveloperConnector}
import uk.gov.hmrc.apiplatformorganisationfrontend.mocks.connectors.UpscanInitiateConnectorMockModule
import uk.gov.hmrc.apiplatformorganisationfrontend.models.upscan.services.UpscanInitiateResponse
import uk.gov.hmrc.apiplatformorganisationfrontend.models.views.UploadViewModel

class SubmissionServiceSpec extends AsyncHmrcSpec with LocalUserIdTracker with UserTestData {

  implicit val ec: ExecutionContext = ExecutionContext.global

  trait Setup extends FixedClock with SubmissionsTestData with UpscanInitiateConnectorMockModule {
    implicit val hc: HeaderCarrier = HeaderCarrier()

    val mockOrganisationConnector        = mock[OrganisationConnector]
    val mockThirdPartyDeveloperConnector = mock[ThirdPartyDeveloperConnector]
    val mockApiPlatformDeskproConnector  = mock[ApiPlatformDeskproConnector]

    val underTest = new SubmissionService(
      mockOrganisationConnector,
      mockThirdPartyDeveloperConnector,
      mockApiPlatformDeskproConnector,
      UpscanInitiateConnectorMock.aMock
    )

    val allowList = OrganisationAllowList(userId, OrganisationName("My Org 1"), "requestedBy", instant)
    val email     = LaxEmailAddress("bob@example.com")

    val ukLtdSubmission: Submission = aSubmission
      .hasCompletelyAnsweredWith(samplePassAnswersToQuestions)
      .withCompletedProgress()
      .submission
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
    "submit submission and update profile when RI name given" in new Setup {
      val extendedSubmission: ExtendedSubmission = completelyAnswerExtendedSubmission.copy(submission = ukLtdSubmission)
      when(mockOrganisationConnector.fetchSubmission(*[SubmissionId])(*)).thenReturn(successful(Some(extendedSubmission)))
      when(mockOrganisationConnector.submitSubmission(*[SubmissionId], *[LaxEmailAddress])(*)).thenReturn(successful(Right(ukLtdSubmission)))
      when(mockThirdPartyDeveloperConnector.updateProfile(*[UserId], *)(*)).thenReturn(successful(standardDeveloper))

      val result = await(underTest.submitSubmission(ukLtdSubmission.id, userId, email, adminDeveloper))

      result.isRight shouldBe true
      verify(mockOrganisationConnector).submitSubmission(eqTo(ukLtdSubmission.id), eqTo(email))(*)
      verify(mockThirdPartyDeveloperConnector).updateProfile(eqTo(userId), eqTo(UpdateRequest("Bob", "Fleming")))(*)
    }

    "submit submission and not update profile when RI name not given" in new Setup {
      val submissionWithNoRIName                 = aSubmission
        .hasCompletelyAnsweredWith(sampleAnswersToQuestions1)
        .withCompletedProgress()
        .submission
      val extendedSubmission: ExtendedSubmission = completelyAnswerExtendedSubmission.copy(submission = submissionWithNoRIName)

      when(mockOrganisationConnector.fetchSubmission(*[SubmissionId])(*)).thenReturn(successful(Some(extendedSubmission)))
      when(mockOrganisationConnector.submitSubmission(*[SubmissionId], *[LaxEmailAddress])(*)).thenReturn(successful(Right(submissionWithNoRIName)))

      val result = await(underTest.submitSubmission(submissionWithNoRIName.id, userId, email, adminDeveloper))

      result.isRight shouldBe true
      verify(mockOrganisationConnector).submitSubmission(eqTo(submissionWithNoRIName.id), eqTo(email))(*)
      verify(mockThirdPartyDeveloperConnector, never).updateProfile(*[UserId], *)(*)
    }

    "submit submission and create support ticket when company type is" +
      " Non-UK without a branch or place of business in the UK" in new Setup {
        val nonUkSubmission                        = aSubmission
          .hasCompletelyAnsweredWith(sampleAnswersToQuestions2)
          .withCompletedProgress()
          .submission
        val ticketRef                              = Some("12345")
        val ticketId                               = Some(12345)
        val extendedSubmission: ExtendedSubmission = completelyAnswerExtendedSubmission.copy(submission = nonUkSubmission)

        when(mockOrganisationConnector.fetchSubmission(*[SubmissionId])(*)).thenReturn(successful(Some(extendedSubmission)))
        when(mockOrganisationConnector.submitSubmission(*[SubmissionId], *[LaxEmailAddress])(*)).thenReturn(successful(Right(nonUkSubmission)))
        when(mockApiPlatformDeskproConnector.createTicket(*, *)).thenReturn(successful(CreateTicketResponse(ticketRef, ticketId)))
        when(mockOrganisationConnector.recordTicketOnSubmission(*[SubmissionId], *, *)(*)).thenReturn(successful(Right(extendedSubmission)))

        val result = await(underTest.submitSubmission(nonUkSubmission.id, userId, email, adminDeveloper))

        result.isRight shouldBe true
        verify(mockOrganisationConnector).submitSubmission(eqTo(nonUkSubmission.id), eqTo(email))(*)
        verify(mockThirdPartyDeveloperConnector, never).updateProfile(*[UserId], *)(*)
        verify(mockApiPlatformDeskproConnector, times(1)).createTicket(*, *)
        verify(mockOrganisationConnector).recordTicketOnSubmission(*[SubmissionId], *, *)(*)
      }

    "submit submission and not create support ticket when company type is UK Ltd" in new Setup {
      val extendedSubmission: ExtendedSubmission = completelyAnswerExtendedSubmission.copy(submission = ukLtdSubmission)

      when(mockOrganisationConnector.fetchSubmission(*[SubmissionId])(*)).thenReturn(successful(Some(extendedSubmission)))
      when(mockOrganisationConnector.submitSubmission(*[SubmissionId], *[LaxEmailAddress])(*)).thenReturn(successful(Right(ukLtdSubmission)))
      when(mockThirdPartyDeveloperConnector.updateProfile(*[UserId], *)(*)).thenReturn(successful(standardDeveloper))

      val result = await(underTest.submitSubmission(ukLtdSubmission.id, userId, email, adminDeveloper))

      result.isRight shouldBe true
      verify(mockOrganisationConnector).submitSubmission(eqTo(ukLtdSubmission.id), eqTo(email))(*)
      verify(mockThirdPartyDeveloperConnector).updateProfile(eqTo(userId), eqTo(UpdateRequest("Bob", "Fleming")))(*)
      verify(mockApiPlatformDeskproConnector, never).createTicket(*, *)
    }

    "fail submit submission when submission is not found" in new Setup {
      when(mockOrganisationConnector.fetchSubmission(*[SubmissionId])(*)).thenReturn(successful(None))

      val result = await(underTest.submitSubmission(ukLtdSubmission.id, userId, email, adminDeveloper))

      result.isLeft shouldBe true
      verify(mockOrganisationConnector, never).submitSubmission(eqTo(ukLtdSubmission.id), eqTo(email))(*)
      verify(mockThirdPartyDeveloperConnector, never).updateProfile(eqTo(userId), eqTo(UpdateRequest("Bob", "Fleming")))(*)
      verify(mockApiPlatformDeskproConnector, never).createTicket(*, *)
    }

    "fail to submit submission when company type is " +
      "Non-UK without a branch or place of business in the UK " +
      "and create ticket fails" in new Setup {
        val nonUkSubmission                        = aSubmission
          .hasCompletelyAnsweredWith(sampleAnswersToQuestions2)
          .withCompletedProgress()
          .submission
        val extendedSubmission: ExtendedSubmission = completelyAnswerExtendedSubmission.copy(submission = nonUkSubmission)

        when(mockOrganisationConnector.fetchSubmission(*[SubmissionId])(*)).thenReturn(successful(Some(extendedSubmission)))
        when(mockApiPlatformDeskproConnector.createTicket(*, *)).thenReturn(Future.failed(UpstreamErrorResponse("error", INTERNAL_SERVER_ERROR)))

        intercept[UpstreamErrorResponse] {
          await(underTest.submitSubmission(nonUkSubmission.id, userId, email, adminDeveloper))
        }.statusCode shouldBe INTERNAL_SERVER_ERROR

        verify(mockOrganisationConnector, never).submitSubmission(eqTo(nonUkSubmission.id), eqTo(email))(*)
        verify(mockThirdPartyDeveloperConnector, never).updateProfile(*[UserId], *)(*)
        verify(mockApiPlatformDeskproConnector, times(1)).createTicket(*, *)
        verify(mockOrganisationConnector, never).recordTicketOnSubmission(*[SubmissionId], *, *)(*)
      }

    "fail to submit submission when company type is " +
      "Non-UK without a branch or place of business in the UK " +
      "and create ticket returns NONE for ticketId and ticketRef" in new Setup {
        val nonUkSubmission                        = aSubmission
          .hasCompletelyAnsweredWith(sampleAnswersToQuestions2)
          .withCompletedProgress()
          .submission
        val ticketRef                              = Some("12345")
        val ticketId                               = Some(12345)
        val extendedSubmission: ExtendedSubmission = completelyAnswerExtendedSubmission.copy(submission = nonUkSubmission)

        when(mockOrganisationConnector.fetchSubmission(*[SubmissionId])(*)).thenReturn(successful(Some(extendedSubmission)))
        when(mockApiPlatformDeskproConnector.createTicket(*, *)).thenReturn(Future.successful(CreateTicketResponse(None, None)))

        val result = await(underTest.submitSubmission(ukLtdSubmission.id, userId, email, adminDeveloper))

        result shouldBe Left(s"Create ticket did not return ticket Id and ticket reference for submission: ${nonUkSubmission.id}")

        verify(mockOrganisationConnector, never).submitSubmission(eqTo(nonUkSubmission.id), eqTo(email))(*)
        verify(mockThirdPartyDeveloperConnector, never).updateProfile(*[UserId], *)(*)
        verify(mockApiPlatformDeskproConnector, times(1)).createTicket(*, *)
        verify(mockOrganisationConnector, never).recordTicketOnSubmission(*[SubmissionId], *, *)(*)
      }

    "fail to submit submission when company type is " +
      "Non-UK without a branch or place of business in the UK " +
      "and record ticket on submission returns ValidationErrors" in new Setup {
        val nonUkSubmission                        = aSubmission
          .hasCompletelyAnsweredWith(sampleAnswersToQuestions2)
          .withCompletedProgress()
          .submission
        val ticketRef                              = Some("12345")
        val ticketId                               = Some(12345)
        val extendedSubmission: ExtendedSubmission = completelyAnswerExtendedSubmission.copy(submission = nonUkSubmission)
        val validationErrorMsg                     = "Validation error message"

        when(mockOrganisationConnector.fetchSubmission(*[SubmissionId])(*)).thenReturn(successful(Some(extendedSubmission)))
        when(mockApiPlatformDeskproConnector.createTicket(*, *)).thenReturn(successful(CreateTicketResponse(ticketRef, ticketId)))
        when(mockOrganisationConnector.recordTicketOnSubmission(*[SubmissionId], *, *)(*)).thenReturn(
          successful(Left(ValidationErrors(ValidationError("key", validationErrorMsg))))
        )

        val result = await(underTest.submitSubmission(nonUkSubmission.id, userId, email, adminDeveloper))

        result shouldBe Left(s"Record ticket on submission failed with validation error: $validationErrorMsg")

        verify(mockOrganisationConnector, never).submitSubmission(eqTo(nonUkSubmission.id), eqTo(email))(*)
        verify(mockThirdPartyDeveloperConnector, never).updateProfile(*[UserId], *)(*)
        verify(mockApiPlatformDeskproConnector, times(1)).createTicket(*, *)
        verify(mockOrganisationConnector).recordTicketOnSubmission(*[SubmissionId], *, *)(*)
      }

    "re-submit submission and update support ticket when company type is " +
      "Non-UK without a branch or place of business in the UK " +
      "and ticketId present on Submission" in new Setup {
        val ticketRef                               = Some("12345")
        val ticketId                                = Some(12345)
        val additionalSubmissionData                = AdditionalSubmissionData(supportTicketId = ticketId, supportTicketRef = ticketRef)
        val nonUkSubmissionWithTicketId: Submission = aSubmission
          .hasCompletelyAnsweredWith(sampleAnswersToQuestions2)
          .withCompletedProgress()
          .submission.copy(additionalSubmissionData = Some(additionalSubmissionData))
        val extendedSubmission: ExtendedSubmission  = completelyAnswerExtendedSubmission.copy(submission = nonUkSubmissionWithTicketId)

        when(mockOrganisationConnector.fetchSubmission(*[SubmissionId])(*)).thenReturn(successful(Some(extendedSubmission)))
        when(mockApiPlatformDeskproConnector.createMessage(*, *[LaxEmailAddress], *, *, *, *)).thenReturn(successful(CreateMessageSuccess))
        when(mockOrganisationConnector.submitSubmission(*[SubmissionId], *[LaxEmailAddress])(*)).thenReturn(successful(Right(nonUkSubmissionWithTicketId)))

        val result: Either[String, Submission] = await(underTest.submitSubmission(extendedSubmission.submission.id, userId, email, adminDeveloper))

        result.isRight shouldBe true
        verify(mockOrganisationConnector).submitSubmission(eqTo(extendedSubmission.submission.id), eqTo(email))(*)
        verify(mockThirdPartyDeveloperConnector, never).updateProfile(*[UserId], *)(*)
        verify(mockApiPlatformDeskproConnector, times(1)).createMessage(*, *[LaxEmailAddress], *, *, *, *)
        verify(mockOrganisationConnector, never).recordTicketOnSubmission(*[SubmissionId], *, *)(*)
      }

    "fail to re-submit submission and update support ticket when company type is " +
      "Non-UK without a branch or place of business in the UK " +
      "create message fails" in new Setup {
        val ticketRef                               = Some("12345")
        val ticketId                                = Some(12345)
        val additionalSubmissionData                = AdditionalSubmissionData(supportTicketId = ticketId, supportTicketRef = ticketRef)
        val nonUkSubmissionWithTicketId: Submission = aSubmission
          .hasCompletelyAnsweredWith(sampleAnswersToQuestions2)
          .withCompletedProgress()
          .submission.copy(additionalSubmissionData = Some(additionalSubmissionData))
        val extendedSubmission: ExtendedSubmission  = completelyAnswerExtendedSubmission.copy(submission = nonUkSubmissionWithTicketId)

        when(mockOrganisationConnector.fetchSubmission(*[SubmissionId])(*)).thenReturn(successful(Some(extendedSubmission)))
        when(mockApiPlatformDeskproConnector.createMessage(*, *[LaxEmailAddress], *, *, *, *)).thenReturn(successful(CreateMessageFailure))
        when(mockOrganisationConnector.submitSubmission(*[SubmissionId], *[LaxEmailAddress])(*)).thenReturn(successful(Right(nonUkSubmissionWithTicketId)))

        val result: Either[String, Submission] = await(underTest.submitSubmission(extendedSubmission.submission.id, userId, email, adminDeveloper))

        result shouldBe Left(s"Create message on ticket failed for submission: ${nonUkSubmissionWithTicketId.id}")
        verify(mockOrganisationConnector, never).submitSubmission(eqTo(extendedSubmission.submission.id), eqTo(email))(*)
        verify(mockThirdPartyDeveloperConnector, never).updateProfile(*[UserId], *)(*)
        verify(mockApiPlatformDeskproConnector, times(1)).createMessage(*, *[LaxEmailAddress], *, *, *, *)
        verify(mockOrganisationConnector, never).recordTicketOnSubmission(*[SubmissionId], *, *)(*)
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

  "initiateUpscan" should {
    "return upload view model when question is AttachmentQuestion" in new Setup {
      val upscanResponse: UpscanInitiateResponse = upscanInitiateResponse(OrganisationDetails.questionNonUkWithoutAttachment.id, aSubmission.id)
      UpscanInitiateConnectorMock.Initiate.succeedsWith(OrganisationDetails.questionNonUkWithoutAttachment.id, aSubmission.id)(upscanResponse)
      val result: Option[UploadViewModel]        = await(underTest.initiateUpscan(OrganisationDetails.questionNonUkWithoutAttachment, aSubmission.id, returnTo = None))

      result.isDefined shouldBe true
      result match {
        case None                                   => fail()
        case Some(uploadViewModel: UploadViewModel) =>
          uploadViewModel.upscan shouldBe upscanResponse
          uploadViewModel.error shouldBe None
      }
    }
    "return None when question is not AttachmentQuestion" in new Setup {
      val result: Option[UploadViewModel] = await(underTest.initiateUpscan(OrganisationDetails.questionCompanyNumber, aSubmission.id, returnTo = None))

      result shouldBe None
      verify(UpscanInitiateConnectorMock.aMock, never).initiate(*[Question.Id], *[SubmissionId], *)(*[HeaderCarrier])
    }
  }
}
