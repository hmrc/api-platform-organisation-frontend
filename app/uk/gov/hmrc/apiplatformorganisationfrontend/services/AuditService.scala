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

package uk.gov.hmrc.apiplatformorganisationfrontend.services

import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.AuditExtensions.auditHeaderCarrier
import uk.gov.hmrc.play.audit.http.connector.{AuditConnector, AuditResult}
import uk.gov.hmrc.play.audit.model.DataEvent

@Singleton
class AuditService @Inject() (auditConnector: AuditConnector)(using val ec: ExecutionContext) {

  def audit(action: AuditAction, data: Map[String, String] = Map.empty)(implicit hc: HeaderCarrier): Future[AuditResult] =
    auditConnector.sendEvent(DataEvent(
      auditSource = "api-platform-organisation-frontend",
      auditType = action.auditType,
      tags = hc.toAuditTags(action.name, "-") ++ userContext(hc) ++ action.tags.toSeq ++ data,
      detail = hc.toAuditDetails(action.details.toSeq*)
    ))

  def userContext(hc: HeaderCarrier): Seq[(String, String)] = {
    def mapHeader(oldKey: String, newKey: String): Option[(String, String)] =
      hc.extraHeaders.toMap
        .get(oldKey)
        .map(value => newKey -> URLDecoder.decode(value, StandardCharsets.UTF_8.toString))

    val devEmail          = mapHeader("X-email-address", "devEmail")
    val developerFullName = mapHeader("X-name", "developerFullName")

    Seq(devEmail, developerFullName).flatten
  }
}

sealed trait AuditAction {
  val auditType: String
  val name: String
  val tags: Map[String, String]    = Map.empty
  val details: Map[String, String] = Map.empty
}

object AuditAction {

  case object OrganisationRegistrationStarted extends AuditAction {
    override val name: String      = "Developer has started organisation registration"
    override val auditType: String = "OrganisationRegistrationStarted"
  }
}
