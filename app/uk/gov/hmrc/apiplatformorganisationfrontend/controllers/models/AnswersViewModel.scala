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

import uk.gov.hmrc.apiplatform.modules.organisations.submissions.domain.models._

object AnswersViewModel {
  case class ViewQuestion(id: Question.Id, text: String, answer: String)
  case class ViewQuestionnaire(label: String, state: String, id: Questionnaire.Id, questions: NonEmptyList[ViewQuestion])
  case class ViewModel(submissionId: SubmissionId, questionnaires: List[ViewQuestionnaire])
  private val dateTimeFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy")

  private def convertAnswer(answer: ActualAnswer): Option[String] = answer match {
    case ActualAnswer.SingleChoiceAnswer(value)    => Some(value)
    case ActualAnswer.TextAnswer(value)            => Some(value)
    case ActualAnswer.DateAnswer(value)            => Some(value.format(dateTimeFormatter))
    case ActualAnswer.MultipleChoiceAnswer(values) => Some(values.mkString)
    case ActualAnswer.AddressAnswer(add)           =>
      Some(Seq(add.addressLineOne, add.addressLineTwo, add.locality, add.region, add.postalCode).filter(_.isDefined).map(_.get).mkString(", "))
    case ActualAnswer.NoAnswer                     => Some("n/a")
    case ActualAnswer.AcknowledgedAnswer           => None
  }

  private def convertQuestion(submission: Submission)(item: QuestionItem): Option[ViewQuestion] = {
    val id = item.question.id

    submission.latestInstance.answersToQuestions.get(id).flatMap(convertAnswer).map(answer =>
      ViewQuestion(id, item.question.wording.value, answer)
    )
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
