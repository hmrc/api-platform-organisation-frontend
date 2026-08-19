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

import uk.gov.hmrc.apiplatform.modules.organisations.submissions.domain.models.Submission

object CompanyDetailsFormatter {

  def addressLines(details: Submission.CompanyDetails): Seq[String] =
    Seq(
      details.careOf,
      details.poBox,
      details.premises,
      details.addressLineOne,
      details.addressLineTwo,
      details.locality,
      details.region,
      details.postalCode,
      details.country
    ).flatten.filter(_.trim.nonEmpty)
}
