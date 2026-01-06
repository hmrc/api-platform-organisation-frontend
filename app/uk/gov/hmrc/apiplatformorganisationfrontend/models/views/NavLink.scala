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

package uk.gov.hmrc.apiplatformorganisationfrontend.models.views

import play.api.libs.json._
import uk.gov.hmrc.govukfrontend.views.Aliases.ServiceNavigationItem
import uk.gov.hmrc.govukfrontend.views.html.components.Text
import uk.gov.hmrc.hmrcfrontend.views.viewmodels.header.NavigationItem

import uk.gov.hmrc.apiplatformorganisationfrontend.config.AppConfig

case class NavLink(label: String, href: String, truncate: Boolean = false, openInNewWindow: Boolean = false, isSensitive: Boolean = false)

object NavLink {
  implicit val format: OFormat[NavLink] = Json.format[NavLink]
}

case object StaticNavItems {

  def apply(apiDocumentationFrontendUrl: String, thirdPartyDeveloperFrontendUrl: String, devhubSupportUrl: String) = {
    Seq(
      ServiceNavigationItem(Text("Getting started"), s"$apiDocumentationFrontendUrl/api-documentation/docs/using-the-hub"),
      ServiceNavigationItem(Text("API documentation"), s"$apiDocumentationFrontendUrl/api-documentation/docs/api"),
      ServiceNavigationItem(Text("Applications"), s"$thirdPartyDeveloperFrontendUrl/developer/applications"),
      ServiceNavigationItem(Text("Support"), s"$devhubSupportUrl"),
      ServiceNavigationItem(Text("Service availability"), "https://api-platform-status.production.tax.service.gov.uk/", attributes = Map("target" -> "_blank"))
    )
  }
}

case object UserNavLinks {

  def apply(appConfig: AppConfig, userFullName: Option[String], isRegistering: Boolean = false) =
    (userFullName, isRegistering) match {
      case (_, true)       => Seq.empty
      case (Some(name), _) => loggedInNavLinks(appConfig, name)
      case (_, _)          => loggedOutNavLinks(appConfig)
    }

  private def loggedInNavLinks(appConfig: AppConfig, userFullName: String) = List(
    NavLink(userFullName, s"${appConfig.thirdPartyDeveloperFrontendUrl}/developer/profile", isSensitive = true),
    NavLink("Sign out", s"${appConfig.thirdPartyDeveloperFrontendUrl}/developer/logout/survey")
  )

  private def loggedOutNavLinks(appConfig: AppConfig) = List(
    NavLink("Register", s"${appConfig.thirdPartyDeveloperFrontendUrl}/developer/registration"),
    NavLink("Sign in", appConfig.logInUrl)
  )
}
