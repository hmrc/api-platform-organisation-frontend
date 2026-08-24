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

package uk.gov.hmrc.apiplatformorganisationfrontend.connectors

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

import play.api.libs.json.{Format, Json, Reads}
import play.api.libs.ws.writeableOf_JsValue
import play.mvc.Http.HeaderNames
import uk.gov.hmrc.http.HttpReads.Implicits.*
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, StringContextOps}

import uk.gov.hmrc.apiplatform.modules.organisations.submissions.domain.models.{Question, SubmissionId}
import uk.gov.hmrc.apiplatformorganisationfrontend.config.AppConfig
import uk.gov.hmrc.apiplatformorganisationfrontend.models.upscan.services.{UpscanFileReference, UpscanInitiateResponse}

case class UpscanInitiateRequest(
    callbackUrl: String,
    successRedirect: Option[String] = None,
    errorRedirect: Option[String] = None,
    minimumFileSize: Option[Int] = Some(1),
    maximumFileSize: Option[Int] = Some(10 * 1024 * 1024) // Max size of 10MB
  )

object UpscanInitiateRequest {
  implicit val upscanInitiateRequestFormat: Format[UpscanInitiateRequest] = Json.format[UpscanInitiateRequest]
}

case class UploadForm(
    href: String,
    fields: Map[String, String]
  )

case class Reference(value: String) extends AnyVal

object Reference {
  implicit val referenceFormat: Reads[Reference] = Reads.StringReads.map(Reference(_))
}

case class PreparedUpload(
    reference: Reference,
    uploadRequest: UploadForm
  )

object PreparedUpload {
  implicit val UploadFormFormat: Reads[UploadForm]         = Json.reads[UploadForm]
  implicit val PreparedUploadFormat: Reads[PreparedUpload] = Json.reads[PreparedUpload]
}

class UpscanInitiateConnector @Inject() (
    httpClient: HttpClientV2,
    appConfig: AppConfig
  )(implicit val ec: ExecutionContext
  ) {

  private val headers = Map(
    HeaderNames.CONTENT_TYPE -> "application/json"
  )

  def initiate(questionId: Question.Id, submissionId: SubmissionId, returnTo: Option[String] = None)(implicit hc: HeaderCarrier): Future[UpscanInitiateResponse] = {
    def queryParams = {
      val qp     = Seq(
        "questionId"   -> questionId.value,
        "submissionId" -> submissionId.value.toString
      )
      val params = returnTo.fold(qp)(rt => qp ++ Seq("returnTo" -> rt))

      params.collect {
        case (key, value) =>
          s"$key=${URLEncoder.encode(value, StandardCharsets.UTF_8.toString)}"
      }.mkString("&")
    }

    val redirectUrl = s"${appConfig.organisationFrontendUrl}/api-platform-organisation/upscan/result?$queryParams"

    val request = UpscanInitiateRequest(
      callbackUrl = appConfig.callbackEndpointTarget,
      successRedirect = Some(redirectUrl),
      errorRedirect = Some(redirectUrl)
    )

    for {
      response     <- httpClient.post(url"${appConfig.initiateV2Url}")
                        .withBody(Json.toJson(request))
                        .setHeader(headers.toSeq: _*)
                        .execute[PreparedUpload]
      fileReference = UpscanFileReference(response.reference.value)
      postTarget    = response.uploadRequest.href
      formFields    = response.uploadRequest.fields
    } yield UpscanInitiateResponse(fileReference, postTarget, formFields)
  }
}
