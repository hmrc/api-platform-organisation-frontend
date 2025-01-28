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

package uk.gov.hmrc.apiplatformorganisationfrontend.controllers

import scala.concurrent.Future.successful
import scala.concurrent.{ExecutionContext, Future}

import cats.instances.future.catsStdInstancesForFuture

import play.api.mvc.{ActionRefiner, _}

import uk.gov.hmrc.apiplatform.modules.organisations.submissions.domain.models.Submission.Status
import uk.gov.hmrc.apiplatform.modules.organisations.submissions.domain.models.{SubmissionId, _}
import uk.gov.hmrc.apiplatformorganisationfrontend.controllers.BaseController
import uk.gov.hmrc.apiplatformorganisationfrontend.controllers.models.UserRequest
import uk.gov.hmrc.apiplatformorganisationfrontend.services.SubmissionService

class SubmissionRequest[A](val extSubmission: ExtendedSubmission, val userRequest: UserRequest[A]) extends UserRequest[A](userRequest.userSession, userRequest.msgRequest) {
  lazy val submission         = extSubmission.submission
  lazy val answersToQuestions = submission.latestInstance.answersToQuestions
}

object SubmissionActionBuilders {

  object SubmissionStatusFilter {
    type Type = Status => Boolean
    val answeredCompletely: Type = _.isAnsweredCompletely
    val submitted: Type          = _.isSubmitted

    val submittedGrantedOrDeclined: Type = status =>
      status.isSubmitted || status.isGranted || status.isGrantedWithWarnings || status.isDeclined || status.isFailed || status.isWarnings || status.isPendingResponsibleIndividual
    val granted: Type                    = status => status.isGranted || status.isGrantedWithWarnings
    val allAllowed: Type                 = _ => true
  }
}

trait SubmissionActionBuilders {
  self: BaseController =>

  import SubmissionActionBuilders.SubmissionStatusFilter

  def submissionService: SubmissionService

  private def submissionRefiner(submissionId: SubmissionId)(implicit ec: ExecutionContext): ActionRefiner[UserRequest, SubmissionRequest] =
    new ActionRefiner[UserRequest, SubmissionRequest] {
      def executionContext = ec

      def refine[A](input: UserRequest[A]): Future[Either[Result, SubmissionRequest[A]]] = {
        implicit val implicitRequest: MessagesRequest[A] = input
        (
          for {
            submission <- ETR.fromOptionM(submissionService.fetch(submissionId), errorHandler.notFoundTemplate(input).map(NotFound(_)))
          } yield new SubmissionRequest(submission, input)
        )
          .value
      }
    }

  private def submissionFilter[SR[_] <: SubmissionRequest[_]](submissionStatusFilter: SubmissionStatusFilter.Type)(redirectOnIncomplete: => Result): ActionFilter[SR] =
    new ActionFilter[SR] {

      override protected def executionContext: ExecutionContext = ec

      override protected def filter[A](request: SR[A]): Future[Option[Result]] =
        if (submissionStatusFilter(request.extSubmission.submission.status)) {
          successful(None)
        } else {
          successful(Some(redirectOnIncomplete))
        }
    }

  def withSubmission(submissionId: SubmissionId)(block: SubmissionRequest[AnyContent] => Future[Result])(implicit ec: ExecutionContext): Action[AnyContent] = {
    Action.async { implicit request =>
      (
        loggedInActionRefiner() andThen
          submissionRefiner(submissionId)
      ).invokeBlock(request, block)
    }
  }
}
