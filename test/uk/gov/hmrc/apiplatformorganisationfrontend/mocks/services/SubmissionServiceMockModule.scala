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

package uk.gov.hmrc.apiplatformorganisationfrontend.mocks.services

import scala.concurrent.Future.successful

import org.mockito.quality.Strictness
import org.mockito.{ArgumentMatchersSugar, MockitoSugar}

import uk.gov.hmrc.apiplatform.modules.common.domain.models._
import uk.gov.hmrc.apiplatform.modules.organisations.submissions.domain.models._
import uk.gov.hmrc.apiplatform.modules.organisations.submissions.domain.services.{ValidationError, ValidationErrors}
import uk.gov.hmrc.apiplatform.modules.tpd.core.domain.models.User
import uk.gov.hmrc.apiplatformorganisationfrontend.services.SubmissionService

trait SubmissionServiceMockModule extends MockitoSugar with ArgumentMatchersSugar {

  protected trait BaseSubmissionServiceMock {
    def aMock: SubmissionService

    object FetchLatestSubmissionByUserId {

      def thenReturns(out: Submission) =
        when(aMock.fetchLatestSubmissionByUserId(*[UserId])(*)).thenReturn(successful(Some(out)))

      def thenReturnsNone() = {
        when(aMock.fetchLatestSubmissionByUserId(*[UserId])(*)).thenReturn(successful(None))
      }
    }

    object FetchLatestExtendedSubmissionByUserId {

      def thenReturns(out: ExtendedSubmission) =
        when(aMock.fetchLatestExtendedSubmissionByUserId(*[UserId])(*)).thenReturn(successful(Some(out)))

      def thenReturnsNone() = {
        when(aMock.fetchLatestExtendedSubmissionByUserId(*[UserId])(*)).thenReturn(successful(None))
      }
    }

    object Fetch {

      def thenReturns(out: ExtendedSubmission)(implicit user: User) = {
        when(aMock.fetch(*[SubmissionId])(*)).thenReturn(successful(Some(out.copy(submission = out.submission.copy(startedBy = user.userId)))))
      }

      def thenReturnsWrongUser(out: ExtendedSubmission) = {
        when(aMock.fetch(*[SubmissionId])(*)).thenReturn(successful(Some(out)))
      }

      def thenReturnsNone() = {
        when(aMock.fetch(*[SubmissionId])(*)).thenReturn(successful(None))
      }
    }

    object RecordAnswer {

      def thenReturns(out: ExtendedSubmission) = {
        when(aMock.recordAnswer(*[SubmissionId], *[Question.Id], *)(*)).thenReturn(successful(Right(out)))
      }

      def thenReturnsForAnswer(answer: Map[String, Seq[String]], out: ExtendedSubmission) = {
        when(aMock.recordAnswer(*[SubmissionId], *[Question.Id], eqTo(answer))(*)).thenReturn(successful(Right(out)))
      }

      def thenReturnsError() = {
        when(aMock.recordAnswer(*[SubmissionId], *[Question.Id], *)(*)).thenReturn(successful(Left(ValidationErrors(ValidationError(message =
          "Failed to record answer for submission"
        )))))
      }
    }

    object CreateSubmission {

      def thenReturns(out: Submission) =
        when(aMock.createSubmission(*[UserId], *[LaxEmailAddress])(*)).thenReturn(successful(Some(out)))

      def thenReturnsNone() = {
        when(aMock.createSubmission(*[UserId], *[LaxEmailAddress])(*)).thenReturn(successful(None))
      }
    }

    object SubmitSubmission {

      def thenReturns(out: Submission) =
        when(aMock.submitSubmission(*[SubmissionId], *[LaxEmailAddress])(*)).thenReturn(successful(Right(out)))

      def thenReturnsError() = {
        when(aMock.submitSubmission(*[SubmissionId], *[LaxEmailAddress])(*)).thenReturn(successful(Left("Failed to submit submission")))
      }
    }
  }

  object SubmissionServiceMock extends BaseSubmissionServiceMock {
    val aMock = mock[SubmissionService](withSettings.strictness(Strictness.LENIENT))
  }
}
