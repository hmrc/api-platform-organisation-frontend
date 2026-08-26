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

package uk.gov.hmrc.apiplatformorganisationfrontend.controllers.models.upscan

object UploadError {

  def toMessageKey(errorCode: String): String = errorCode.takeWhile(_ != ' ') match {
    case "EntityTooLarge"    => "uploadFile.entityTooLarge"
    case "InvalidArgument"   => "uploadFile.invalidArgument"
    case "UnexpectedContent" => "uploadFile.rejected"
    case "EntityTooSmall"    =>
      // Upscan has an issue detecting whether a file has been selected when minimum file size is 0.
      // Setting a positive minimum file size causes an unselected file to produce EntityTooSmall,
      // which we map to the same message as InvalidArgument so the user sees the expected error.
      "uploadFile.invalidArgument"
    case _                   => "uploadFile.failed"
  }
}
