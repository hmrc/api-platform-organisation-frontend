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

package uk.gov.hmrc.apiplatformorganisationfrontend.connectors

import org.scalatestplus.play.guice.GuiceOneAppPerSuite

import play.api.http.Status._
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.{Application => PlayApplication, Configuration, Mode}
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}

import uk.gov.hmrc.apiplatform.modules.common.domain.models.LaxEmailAddress
import uk.gov.hmrc.apiplatform.modules.organisations.domain.models.{Member, Organisation, OrganisationId, OrganisationName}
import uk.gov.hmrc.apiplatform.modules.organisations.submissions.utils.SubmissionsTestData
import uk.gov.hmrc.apiplatformorganisationfrontend.stubs.ApiPlatformOrganisationStub

class OrganisationConnectorIntegrationSpec extends BaseConnectorIntegrationSpec with GuiceOneAppPerSuite {

  private val stubConfig = Configuration(
    "microservice.services.api-platform-organisation.port" -> stubPort
  )

  trait Setup extends SubmissionsTestData {
    implicit val hc: HeaderCarrier = HeaderCarrier()
    val underTest                  = app.injector.instanceOf[OrganisationConnector]

    val orgId        = OrganisationId.random
    val organisation = Organisation(orgId, OrganisationName("Org name"), Set(Member(userId)))
  }

  override def fakeApplication(): PlayApplication =
    GuiceApplicationBuilder()
      .configure(stubConfig)
      .in(Mode.Test)
      .build()

  "createSubmission" should {
    val requestedBy = LaxEmailAddress("bob@example.com")
    "successfully create one" in new Setup {
      ApiPlatformOrganisationStub.CreateSubmission.succeeds(userId, aSubmission)

      val result = await(underTest.createSubmission(userId, requestedBy))

      result.isDefined shouldBe true
      result.get.startedBy shouldBe userId
    }

    "fail when the creation call returns an error" in new Setup {
      ApiPlatformOrganisationStub.CreateSubmission.fails(userId, INTERNAL_SERVER_ERROR)

      intercept[UpstreamErrorResponse] {
        await(underTest.createSubmission(userId, requestedBy))
      }.statusCode shouldBe INTERNAL_SERVER_ERROR
    }
  }

  "submitSubmission" should {
    val requestedBy = LaxEmailAddress("bob@example.com")
    "successfully submit" in new Setup {
      ApiPlatformOrganisationStub.SubmitSubmission.succeeds(submissionId, aSubmission)

      val result = await(underTest.submitSubmission(submissionId, requestedBy))

      result.isRight shouldBe true
    }

    "fail when the submit returns an error" in new Setup {
      ApiPlatformOrganisationStub.SubmitSubmission.fails(submissionId, INTERNAL_SERVER_ERROR)

      val result = await(underTest.submitSubmission(submissionId, requestedBy))

      result.isLeft shouldBe true
    }
  }

  "fetchSubmission" should {
    "successfully get one" in new Setup {
      ApiPlatformOrganisationStub.FetchSubmission.succeeds(completelyAnswerExtendedSubmission.submission.id, completelyAnswerExtendedSubmission)

      val result = await(underTest.fetchSubmission(completelyAnswerExtendedSubmission.submission.id))

      result shouldBe Some(completelyAnswerExtendedSubmission)
    }

    "return None when not found" in new Setup {
      ApiPlatformOrganisationStub.FetchSubmission.fails(submissionId, NOT_FOUND)

      val result = await(underTest.fetchSubmission(submissionId))

      result shouldBe None
    }

    "fail when the call returns an error" in new Setup {
      ApiPlatformOrganisationStub.FetchSubmission.fails(submissionId, INTERNAL_SERVER_ERROR)

      intercept[UpstreamErrorResponse] {
        await(underTest.fetchSubmission(submissionId))
      }.statusCode shouldBe INTERNAL_SERVER_ERROR
    }
  }

  "fetchLatestExtendedSubmissionByUserId" should {
    "successfully get one" in new Setup {
      ApiPlatformOrganisationStub.FetchLatestExtendedSubmissionByUserId.succeeds(userId, completelyAnswerExtendedSubmission)

      val result = await(underTest.fetchLatestExtendedSubmissionByUserId(userId))

      result shouldBe Some(completelyAnswerExtendedSubmission)
    }

    "return None when not found" in new Setup {
      ApiPlatformOrganisationStub.FetchLatestExtendedSubmissionByUserId.fails(userId, NOT_FOUND)

      val result = await(underTest.fetchLatestExtendedSubmissionByUserId(userId))

      result shouldBe None
    }

    "fail when the call returns an error" in new Setup {
      ApiPlatformOrganisationStub.FetchLatestExtendedSubmissionByUserId.fails(userId, INTERNAL_SERVER_ERROR)

      intercept[UpstreamErrorResponse] {
        await(underTest.fetchLatestExtendedSubmissionByUserId(userId))
      }.statusCode shouldBe INTERNAL_SERVER_ERROR
    }
  }

  "fetchLatestSubmissionByUserId" should {
    "successfully get one" in new Setup {
      ApiPlatformOrganisationStub.FetchLatestSubmissionByUserId.succeeds(userId, aSubmission)

      val result = await(underTest.fetchLatestSubmissionByUserId(userId))

      result shouldBe Some(aSubmission)
    }

    "return None when not found" in new Setup {
      ApiPlatformOrganisationStub.FetchLatestSubmissionByUserId.fails(userId, NOT_FOUND)

      val result = await(underTest.fetchLatestSubmissionByUserId(userId))

      result shouldBe None
    }

    "fail when the call returns an error" in new Setup {
      ApiPlatformOrganisationStub.FetchLatestSubmissionByUserId.fails(userId, INTERNAL_SERVER_ERROR)

      intercept[UpstreamErrorResponse] {
        await(underTest.fetchLatestSubmissionByUserId(userId))
      }.statusCode shouldBe INTERNAL_SERVER_ERROR
    }
  }

  "recordAnswer" should {
    "successfully record answer" in new Setup {
      ApiPlatformOrganisationStub.RecordAnswer.succeeds(submissionId, aSubmission.questionIdsOfInterest.organisationNameLtdId, completelyAnswerExtendedSubmission)

      val result = await(underTest.recordAnswer(submissionId, aSubmission.questionIdsOfInterest.organisationNameLtdId, Map("answer" -> Seq("answer"))))

      result shouldBe Right(completelyAnswerExtendedSubmission)
    }

    "fail when the creation call returns an error" in new Setup {
      ApiPlatformOrganisationStub.RecordAnswer.fails(submissionId, aSubmission.questionIdsOfInterest.organisationNameLtdId, INTERNAL_SERVER_ERROR)

      val result = await(underTest.recordAnswer(submissionId, aSubmission.questionIdsOfInterest.organisationNameLtdId, Map("answer" -> Seq("answer"))))

      result.isLeft shouldBe true
    }
  }

  "fetchOrganisation" should {
    "successfully get one" in new Setup {
      ApiPlatformOrganisationStub.FetchOrganisation.succeeds(orgId, organisation)

      val result = await(underTest.fetchOrganisation(orgId))

      result shouldBe Some(organisation)
    }

    "return None when not found" in new Setup {
      ApiPlatformOrganisationStub.FetchOrganisation.fails(orgId, NOT_FOUND)

      val result = await(underTest.fetchOrganisation(orgId))

      result shouldBe None
    }

    "fail when the call returns an error" in new Setup {
      ApiPlatformOrganisationStub.FetchOrganisation.fails(orgId, INTERNAL_SERVER_ERROR)

      intercept[UpstreamErrorResponse] {
        await(underTest.fetchOrganisation(orgId))
      }.statusCode shouldBe INTERNAL_SERVER_ERROR
    }
  }

  "addMemberToOrganisation" should {
    "successfully add one" in new Setup {
      ApiPlatformOrganisationStub.AddMemberToOrganisation.succeeds(orgId, organisation)

      val result = await(underTest.addMemberToOrganisation(orgId, userId))

      result shouldBe Right(organisation)
    }

    "fail when the call returns an error" in new Setup {
      ApiPlatformOrganisationStub.AddMemberToOrganisation.fails(orgId, INTERNAL_SERVER_ERROR)

      val result = await(underTest.addMemberToOrganisation(orgId, userId))

      result shouldBe Left(s"Failed to add user $userId to organisation $orgId")
    }
  }

  "removeMemberFromOrganisation" should {
    "successfully remove one" in new Setup {
      ApiPlatformOrganisationStub.RemoveMemberFromOrganisation.succeeds(orgId, userId, organisation)

      val result = await(underTest.removeMemberFromOrganisation(orgId, userId))

      result shouldBe Right(organisation)
    }

    "fail when the call returns an error" in new Setup {
      ApiPlatformOrganisationStub.RemoveMemberFromOrganisation.fails(orgId, userId, INTERNAL_SERVER_ERROR)

      val result = await(underTest.removeMemberFromOrganisation(orgId, userId))

      result shouldBe Left(s"Failed to remove user $userId from organisation $orgId")
    }
  }
}
