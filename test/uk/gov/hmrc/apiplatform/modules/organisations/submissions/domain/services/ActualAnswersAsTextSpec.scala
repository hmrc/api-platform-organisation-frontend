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

package uk.gov.hmrc.apiplatform.modules.organisations.submissions.domain.services

import uk.gov.hmrc.apiplatform.modules.common.utils.{FixedClock, HmrcSpec}
import uk.gov.hmrc.apiplatform.modules.organisations.submissions.domain.models._

class ActualAnswersAsTextSpec extends HmrcSpec with FixedClock {

  "ActualAnswersAsText" should {
    "SingleChoiceAnswer" in {
      ActualAnswersAsText(ActualAnswer.SingleChoiceAnswer("Yes")) shouldBe "Yes"
    }

    "TextAnswer" in {
      ActualAnswersAsText(ActualAnswer.TextAnswer("Text answer")) shouldBe "Text answer"
    }

    "MultipleChoiceAnswer" in {
      ActualAnswersAsText(ActualAnswer.MultipleChoiceAnswer(Set("ChoiceA", "ChoiceB"))) shouldBe "ChoiceAChoiceB"
    }
    "DateAnswer" in {
      ActualAnswersAsText(ActualAnswer.DateAnswer(now.toLocalDate)) shouldBe "02 Jan 2020"
    }
    "NoAnswer" in {
      ActualAnswersAsText(ActualAnswer.NoAnswer) shouldBe "n/a"
    }

    "AcknowledgedAnswer" in {
      ActualAnswersAsText(ActualAnswer.AcknowledgedAnswer) shouldBe ""
    }
  }
}
