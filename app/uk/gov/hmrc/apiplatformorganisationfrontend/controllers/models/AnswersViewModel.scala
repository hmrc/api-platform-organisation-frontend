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

package uk.gov.hmrc.apiplatformorganisationfrontend.controllers.models

import java.time.format.DateTimeFormatter

import cats.data.NonEmptyList

import uk.gov.hmrc.apiplatform.modules.organisations.submissions.domain.models.*
import uk.gov.hmrc.apiplatform.modules.organisations.submissions.domain.models.ActualAnswer.*
import uk.gov.hmrc.apiplatform.modules.organisations.submissions.domain.models.Question.*
import uk.gov.hmrc.apiplatform.modules.organisations.submissions.domain.services.ActualAnswersAsText

object AnswersViewModel {
  case class ViewQuestion(id: Id, text: String, answerLines: Seq[String], questionSummary: Option[String], canChange: Boolean)
  case class ViewQuestionnaire(label: String, state: String, id: Questionnaire.Id, questions: NonEmptyList[ViewQuestion])
  case class ViewModel(submissionId: SubmissionId, questionnaires: List[ViewQuestionnaire])
  private val dateTimeFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy")
  private val notAvailable      = "n/a"
  private val notConfirmed      = "Not confirmed"

  private def convertAnswer(question: Question, answer: ActualAnswer, submission: Submission): Option[Seq[String]] =
    (question, answer) match {
      case (_: ConfirmCompanyNameQuestion, SingleChoiceAnswer("Yes")) =>
        Some(Seq(submission.organisationName.filter(_.trim.nonEmpty).getOrElse(notAvailable)))
      case (_: ConfirmCompanyNameQuestion, SingleChoiceAnswer("No"))  => Some(Seq(notConfirmed))
      case (_: ConfirmCompanyAddressQuestion, _: SingleChoiceAnswer)  =>
        Some(submission.latestInstance.companyDetails.map(CompanyDetailsFormatter.addressLines).filter(_.nonEmpty).getOrElse(Seq(notAvailable)))
      case (_, SingleChoiceAnswer(value))                             => Some(Seq(value))
      case (_, TextAnswer(value))                                     => Some(Seq(value))
      case (_, DateAnswer(value))                                     => Some(Seq(value.format(dateTimeFormatter)))
      case (_, MultipleChoiceAnswer(values))                          => Some(Seq(values.mkString))
      case (_, AddressAnswer(_))                                      => Some(Seq(ActualAnswersAsText.apply(answer)))
      case (_, NameAnswer(name))                                      =>
        Some(Seq(Seq(name.firstName, name.lastName).filter(_.isDefined).map(_.get).mkString(" ")))
      case (_, CompanyNumberAnswer(value))                            => Some(Seq(value))
      case (_, NoAnswer)                                              => Some(Seq(notAvailable))
      case (_, AcknowledgedAnswer)                                    => None
      case (_, AttachmentAnswer(value))                               => Some(Seq("File uploaded"))
    }

  private def canChange(question: Question): Boolean = {
    question match {
      case _: Question.ConfirmCompanyNameQuestion    => false
      case _: Question.ConfirmCompanyAddressQuestion => false
      case _                                         => true
    }
  }

  private def convertQuestion(submission: Submission)(item: QuestionItem): Option[ViewQuestion] = {
    val id = item.question.id

    submission.latestInstance.answersToQuestions.get(id)
      .flatMap(answer => convertAnswer(item.question, answer, submission))
      .map(lines => ViewQuestion(id, item.question.wording.value, lines, item.question.summary, canChange(item.question)))
  }

  private def convertQuestionnaire(extSubmission: ExtendedSubmission)(questionnaire: Questionnaire): Option[ViewQuestionnaire] = {
    val progress = extSubmission.questionnaireProgress.get(questionnaire.id).get
    val state    = QuestionnaireState.describe(progress.state)

    val questions = questionnaire.questions
      .map(convertQuestion(extSubmission.submission))
      .collect { case Some(x) => x }
    NonEmptyList.fromList(questions)
      .map(ViewQuestionnaire(questionnaire.label.value, state, questionnaire.id, _))
  }

  def convertSubmissionToViewModel(extSubmission: ExtendedSubmission): ViewModel = {
    val questionnaires = extSubmission.submission.groups.flatMap(g => g.links)
      .map(convertQuestionnaire(extSubmission))
      .collect { case Some(x) => x }

    ViewModel(extSubmission.submission.id, questionnaires)
  }
}
