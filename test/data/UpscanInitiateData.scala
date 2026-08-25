/*
 * Copyright 2026 HM Revenue & Customs
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

package data

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

import uk.gov.hmrc.apiplatform.modules.organisations.submissions.domain.models.{Question, SubmissionId}
import uk.gov.hmrc.apiplatform.modules.organisations.submissions.utils.{QuestionnaireTestData, SubmissionsTestData}
import uk.gov.hmrc.apiplatformorganisationfrontend.models.upscan.services.{UpscanFileReference, UpscanInitiateResponse}

trait UpscanInitiateData extends QuestionnaireTestData with SubmissionsTestData {

  val fileReference                   = UpscanFileReference("a3d534e5-4de8-438b-afdf-dcadf5e7a23b")
  val formFields: Map[String, String] = Map("key1" -> "value1", "key2" -> "value2", "key3" -> "value3")

  def postTarget(questionId: Question.Id, submissionId: SubmissionId): String = {
    s"/upscan/result?questionId=${questionId.value}&submissionId=${submissionId.value.toString}"
  }

  def upscanInitiateResponse(questionId: Question.Id, submissionId: SubmissionId): UpscanInitiateResponse = {
    UpscanInitiateResponse(fileReference, postTarget(questionId, submissionId), formFields)
  }

  def queryParams(questionId: Question.Id, submissionId: SubmissionId): Seq[(String, String)] = {
    Seq(
      "questionId"   -> questionId.value,
      "submissionId" -> submissionId.value.toString
    )
  }

  def queryParamsAsString(questionId: Question.Id, submissionId: SubmissionId) = {
    queryParams(questionId, submissionId).collect {
      case (key, value) =>
        s"$key=${URLEncoder.encode(value, StandardCharsets.UTF_8.toString)}"
    }.mkString("&")
  }
}
