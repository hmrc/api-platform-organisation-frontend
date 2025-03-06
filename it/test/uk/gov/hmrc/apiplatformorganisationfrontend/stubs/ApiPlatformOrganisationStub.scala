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

package uk.gov.hmrc.apiplatformorganisationfrontend.stubs

import com.github.tomakehurst.wiremock.client.WireMock.{aResponse, get, post, stubFor, urlEqualTo}
import com.github.tomakehurst.wiremock.stubbing.StubMapping

import play.api.http.Status.OK
import play.api.libs.json.Json

import uk.gov.hmrc.apiplatform.modules.common.domain.models.UserId
import uk.gov.hmrc.apiplatform.modules.organisations.domain.models.{Organisation, OrganisationId}
import uk.gov.hmrc.apiplatform.modules.organisations.submissions.domain.models.{ExtendedSubmission, Question, Submission, SubmissionId}

object ApiPlatformOrganisationStub {

  import uk.gov.hmrc.apiplatform.modules.organisations.submissions.domain.models.Submission._

  object CreateSubmission {

    def succeeds(userId: UserId, submission: Submission): StubMapping = {
      stubFor(
        post(urlEqualTo(s"/submission/user/${userId}"))
          .willReturn(
            aResponse()
              .withStatus(OK)
              .withHeader("Content-Type", "application/json")
              .withBody(Json.toJson(submission).toString())
          )
      )
    }

    def fails(userId: UserId, status: Int): StubMapping = {
      stubFor(
        post(urlEqualTo(s"/submission/user/${userId}"))
          .willReturn(
            aResponse()
              .withStatus(status)
          )
      )
    }
  }

  object SubmitSubmission {

    def succeeds(submissionId: SubmissionId, submission: Submission): StubMapping = {
      stubFor(
        post(urlEqualTo(s"/submission/${submissionId}"))
          .willReturn(
            aResponse()
              .withStatus(OK)
              .withHeader("Content-Type", "application/json")
              .withBody(Json.toJson(submission).toString())
          )
      )
    }

    def fails(submissionId: SubmissionId, status: Int): StubMapping = {
      stubFor(
        post(urlEqualTo(s"/submission/${submissionId}"))
          .willReturn(
            aResponse()
              .withStatus(status)
          )
      )
    }
  }

  object FetchSubmission {

    def succeeds(submissionId: SubmissionId, extendedSubmission: ExtendedSubmission): StubMapping = {
      stubFor(
        get(urlEqualTo(s"/submission/$submissionId"))
          .willReturn(
            aResponse()
              .withStatus(OK)
              .withHeader("Content-Type", "application/json")
              .withBody(Json.toJson(extendedSubmission).toString())
          )
      )
    }

    def fails(submissionId: SubmissionId, status: Int): StubMapping = {
      stubFor(
        get(urlEqualTo(s"/submission/$submissionId"))
          .willReturn(
            aResponse()
              .withStatus(status)
          )
      )
    }
  }

  object FetchLatestExtendedSubmissionByUserId {

    def succeeds(userId: UserId, extendedSubmission: ExtendedSubmission): StubMapping = {
      stubFor(
        get(urlEqualTo(s"/submission/user/$userId/extended"))
          .willReturn(
            aResponse()
              .withStatus(OK)
              .withHeader("Content-Type", "application/json")
              .withBody(Json.toJson(extendedSubmission).toString())
          )
      )
    }

    def fails(userId: UserId, status: Int): StubMapping = {
      stubFor(
        get(urlEqualTo(s"/submission/user/$userId/extended"))
          .willReturn(
            aResponse()
              .withStatus(status)
          )
      )
    }
  }

  object FetchLatestSubmissionByUserId {

    def succeeds(userId: UserId, submission: Submission): StubMapping = {
      stubFor(
        get(urlEqualTo(s"/submission/user/$userId"))
          .willReturn(
            aResponse()
              .withStatus(OK)
              .withHeader("Content-Type", "application/json")
              .withBody(Json.toJson(submission).toString())
          )
      )
    }

    def fails(userId: UserId, status: Int): StubMapping = {
      stubFor(
        get(urlEqualTo(s"/submission/user/$userId"))
          .willReturn(
            aResponse()
              .withStatus(status)
          )
      )
    }
  }

  object RecordAnswer {

    def succeeds(submissionId: SubmissionId, questionId: Question.Id, extendedSubmission: ExtendedSubmission): StubMapping = {
      stubFor(
        post(urlEqualTo(s"/submission/$submissionId/question/${questionId.value}"))
          .willReturn(
            aResponse()
              .withStatus(OK)
              .withHeader("Content-Type", "application/json")
              .withBody(Json.toJson(extendedSubmission).toString())
          )
      )
    }

    def fails(submissionId: SubmissionId, questionId: Question.Id, status: Int): StubMapping = {
      stubFor(
        post(urlEqualTo(s"/submission/$submissionId/question/${questionId.value}"))
          .willReturn(
            aResponse()
              .withStatus(status)
          )
      )
    }
  }

  object FetchOrganisation {

    def succeeds(orgId: OrganisationId, organisation: Organisation): StubMapping = {
      stubFor(
        get(urlEqualTo(s"/organisation/$orgId"))
          .willReturn(
            aResponse()
              .withStatus(OK)
              .withHeader("Content-Type", "application/json")
              .withBody(Json.toJson(organisation).toString())
          )
      )
    }

    def fails(orgId: OrganisationId, status: Int): StubMapping = {
      stubFor(
        get(urlEqualTo(s"/organisation/$orgId"))
          .willReturn(
            aResponse()
              .withStatus(status)
          )
      )
    }
  }

  object AddMemberToOrganisation {

    def succeeds(orgId: OrganisationId, organisation: Organisation): StubMapping = {
      stubFor(
        post(urlEqualTo(s"/organisation/$orgId/add-member"))
          .willReturn(
            aResponse()
              .withStatus(OK)
              .withHeader("Content-Type", "application/json")
              .withBody(Json.toJson(organisation).toString())
          )
      )
    }

    def fails(orgId: OrganisationId, status: Int): StubMapping = {
      stubFor(
        post(urlEqualTo(s"/organisation/$orgId/add-member"))
          .willReturn(
            aResponse()
              .withStatus(status)
          )
      )
    }
  }

  object RemoveMemberFromOrganisation {

    def succeeds(orgId: OrganisationId, organisation: Organisation): StubMapping = {
      stubFor(
        post(urlEqualTo(s"/organisation/$orgId/remove-member"))
          .willReturn(
            aResponse()
              .withStatus(OK)
              .withHeader("Content-Type", "application/json")
              .withBody(Json.toJson(organisation).toString())
          )
      )
    }

    def fails(orgId: OrganisationId, status: Int): StubMapping = {
      stubFor(
        post(urlEqualTo(s"/organisation/$orgId/remove-member"))
          .willReturn(
            aResponse()
              .withStatus(status)
          )
      )
    }
  }
}
