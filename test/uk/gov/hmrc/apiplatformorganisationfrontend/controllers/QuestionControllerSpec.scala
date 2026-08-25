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

package uk.gov.hmrc.apiplatformorganisationfrontend.controllers

import scala.concurrent.ExecutionContext.Implicits.global

import cats.data.NonEmptyList
import org.scalatest.AppendedClues
import org.scalatestplus.play.guice.GuiceOneAppPerSuite

import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.crypto.CookieSigner
import play.api.mvc.MessagesControllerComponents
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import play.filters.csrf.CSRF
import uk.gov.hmrc.http.HeaderCarrier

import uk.gov.hmrc.apiplatform.modules.common.utils.{FixedClock, HmrcSpec}
import uk.gov.hmrc.apiplatform.modules.organisations.submissions.domain.models.*
import uk.gov.hmrc.apiplatform.modules.organisations.submissions.domain.services.{ValidationError, ValidationErrors}
import uk.gov.hmrc.apiplatform.modules.organisations.submissions.utils.SubmissionsTestData
import uk.gov.hmrc.apiplatform.modules.tpd.core.domain.models.User
import uk.gov.hmrc.apiplatform.modules.tpd.test.utils.LocalUserIdTracker
import uk.gov.hmrc.apiplatformorganisationfrontend.WithCSRFAddToken
import uk.gov.hmrc.apiplatformorganisationfrontend.WithLoggedInSession.*
import uk.gov.hmrc.apiplatformorganisationfrontend.config.{AppConfig, ErrorHandler}
import uk.gov.hmrc.apiplatformorganisationfrontend.mocks.connectors.{ThirdPartyDeveloperConnectorMockModule, UpscanInitiateConnectorMockModule}
import uk.gov.hmrc.apiplatformorganisationfrontend.mocks.services.{OrganisationActionServiceMockModule, SubmissionServiceMockModule}
import uk.gov.hmrc.apiplatformorganisationfrontend.views.html.{CheckAnswersView, QuestionView}

class QuestionControllerSpec
    extends HmrcSpec
    with GuiceOneAppPerSuite
    with WithCSRFAddToken
    with LocalUserIdTracker
    with SubmissionsTestData {

  trait HasSessionDeveloperFlow {
    val sessionParams = Seq("csrfToken" -> app.injector.instanceOf[CSRF.TokenProvider].generateToken)
  }

  override def fakeApplication(): Application = new GuiceApplicationBuilder().build()

  trait Setup
      extends SubmissionServiceMockModule
      with SubmissionsTestData
      with ThirdPartyDeveloperConnectorMockModule
      with OrganisationActionServiceMockModule
      with UpscanInitiateConnectorMockModule
      with HasSessionDeveloperFlow
      with AppendedClues
      with FixedClock
      with LocalUserIdTracker {

    implicit val hc: HeaderCarrier = HeaderCarrier()
    val mcc                        = app.injector.instanceOf[MessagesControllerComponents]
    val cookieSigner               = app.injector.instanceOf[CookieSigner]
    val errorHandler               = app.injector.instanceOf[ErrorHandler]

    val questionView                  = app.injector.instanceOf[QuestionView]
    val checkAnswersView              = app.injector.instanceOf[CheckAnswersView]
    implicit val appConfig: AppConfig = app.injector.instanceOf[AppConfig]

    val controller = new QuestionsController(
      errorHandler,
      SubmissionServiceMock.aMock,
      OrganisationActionServiceMock.aMock,
      UpscanInitiateConnectorMock.aMock,
      cookieSigner,
      questionView,
      mcc,
      ThirdPartyDeveloperConnectorMock.aMock
    )(global, appConfig)

    val loggedInRequest             = FakeRequest().withUser(controller)(sessionId).withSession(sessionParams*)
    implicit val loggedInUser: User = user

    ThirdPartyDeveloperConnectorMock.FetchSession.succeeds()
  }

  "showQuestion" should {
    "succeed" in new Setup {
      SubmissionServiceMock.Fetch.thenReturns(aSubmission.withIncompleteProgress())

      val formSubmissionLink = s"${aSubmission.id.value}/question/${questionId.value}"
      val result             = controller.showQuestion(aSubmission.id, questionId)(loggedInRequest.withCSRFToken)

      status(result) shouldBe OK
      contentAsString(result).contains(formSubmissionLink) shouldBe true withClue (s"(HTML content did not contain $formSubmissionLink)")
    }

    "succeed and check for label, hintText, name question" in new Setup {
      SubmissionServiceMock.Fetch.thenReturns(aSubmission.withIncompleteProgress())

      val formSubmissionLink = s"${aSubmission.id.value}/question/${ResponsibleIndividualDetails.question2.id.value}"
      val result             = controller.showQuestion(aSubmission.id, ResponsibleIndividualDetails.question2.id)(loggedInRequest.withCSRFToken)

      status(result) shouldBe OK

      contentAsString(result).contains(formSubmissionLink) shouldBe true withClue (s"(HTML content did not contain $formSubmissionLink)")
      contentAsString(result).contains("Who is responsible for the software in your organisation?") shouldBe true withClue ("HTML content did not contain title")
      contentAsString(result).contains("Enter responsible individual details") shouldBe true withClue ("HTML content did not contain caption")
      contentAsString(result).contains(
        s"${loggedInUser.firstName} ${loggedInUser.lastName}"
      ) shouldBe true withClue (s"HTML content did not contain User's name i.e. ${loggedInUser.firstName} ${loggedInUser.lastName}")
      contentAsString(result).contains("Yes") shouldBe true withClue ("HTML content did not contain Radio button -> Yes")
      contentAsString(result).contains("No") shouldBe true withClue ("HTML content did not contain Radio button -> No")
      contentAsString(result).contains("First name") shouldBe true withClue ("HTML content did not contain label for First name")
      contentAsString(result).contains("Last name") shouldBe true withClue ("HTML content did not contain label for Last name")
      contentAsString(
        result
      ).contains(s"""govuk-radios--inline""") shouldBe true withClue ("HTML content did not contain class radios inline")
    }

    "succeed and check for label, hintText, text question" in new Setup {
      SubmissionServiceMock.Fetch.thenReturns(aSubmission.withIncompleteProgress())

      val formSubmissionLink = s"${aSubmission.id.value}/question/${OrganisationDetails.questionLtdOrgUtr.id.value}"
      val result             = controller.showQuestion(aSubmission.id, OrganisationDetails.questionLtdOrgUtr.id)(loggedInRequest.withCSRFToken)

      status(result) shouldBe OK
      contentAsString(result).contains(formSubmissionLink) shouldBe true withClue (s"(HTML content did not contain $formSubmissionLink)")
      contentAsString(result).contains("What is your Corporation Tax Unique Taxpayer Reference (UTR)?") shouldBe true withClue ("HTML content did not contain label")
      contentAsString(result).contains("It will be on tax returns and other letters about Corporation Tax.") shouldBe true withClue ("HTML content did not contain label")
      contentAsString(result).contains("Your UTR can be 10 or 13 digits long.") shouldBe true withClue ("HTML content did not contain hintText")
      contentAsString(
        result
      ).contains(s"""aria-describedby="answer-hint"""") shouldBe true withClue ("HTML content did not contain describeBy")
      contentAsString(result).contains("<title>") shouldBe true
      contentAsString(result).contains("Enter organisation details") shouldBe true withClue ("HTML content did not contain questionnaire name")
    }

    "succeed and check for label, hintText, companyNumber question" in new Setup {
      SubmissionServiceMock.Fetch.thenReturns(aSubmission.withIncompleteProgress())

      val formSubmissionLink = s"${aSubmission.id.value}/question/${OrganisationDetails.questionCompanyNumber.id.value}"
      val result             = controller.showQuestion(aSubmission.id, OrganisationDetails.questionCompanyNumber.id)(loggedInRequest.withCSRFToken)

      status(result) shouldBe OK
      contentAsString(result).contains(formSubmissionLink) shouldBe true withClue (s"(HTML content did not contain $formSubmissionLink)")
      contentAsString(result).contains("What is the company registration number?") shouldBe true withClue ("HTML content did not contain label")
      contentAsString(result).contains("It is 8 characters. For example, 01234567 or AC012345.") shouldBe true withClue ("HTML content did not contain hintText")
      contentAsString(
        result
      ).contains(s"""aria-describedby="answer-hint"""") shouldBe true withClue ("HTML content did not contain describeBy")
      contentAsString(result).contains("<title>") shouldBe true
      contentAsString(result).contains("Enter organisation details") shouldBe true withClue ("HTML content did not contain questionnaire name")
    }

    "succeed and check for label, hintText, date question" in new Setup {
      SubmissionServiceMock.Fetch.thenReturns(aSubmission.withIncompleteProgress())

      val formSubmissionLink = s"${aSubmission.id.value}/question/${OrganisationDetails.questionDate.id.value}"
      val result             = controller.showQuestion(aSubmission.id, OrganisationDetails.questionDate.id)(loggedInRequest.withCSRFToken)

      status(result) shouldBe OK
      contentAsString(result).contains(formSubmissionLink) shouldBe true withClue (s"(HTML content did not contain $formSubmissionLink)")
      contentAsString(result).contains("What date was your organisation founded?") shouldBe true withClue ("HTML content did not contain label")
      contentAsString(result).contains(
        """This is some details<a class="govuk-link" href="https://example.com" target="_blank">with a link</a>"""
      ) shouldBe true withClue ("HTML content did not contain link")
      contentAsString(result).contains("<title>") shouldBe true
    }

    "succeed and check for label, hintText, address question" in new Setup {
      SubmissionServiceMock.Fetch.thenReturns(aSubmission.withIncompleteProgress())

      val formSubmissionLink = s"${aSubmission.id.value}/question/${OrganisationDetails.questionAddress.id.value}"
      val result             = controller.showQuestion(aSubmission.id, OrganisationDetails.questionAddress.id)(loggedInRequest.withCSRFToken)

      status(result) shouldBe OK
      contentAsString(result).contains(formSubmissionLink) shouldBe true withClue (s"(HTML content did not contain $formSubmissionLink)")
      print(contentAsString(result))
      contentAsString(result).contains("What is your organisation&#x27;s address?") shouldBe true withClue ("HTML content did not contain label")
      contentAsString(result).contains("Address line 1") shouldBe true withClue ("HTML content did not contain first input")
      contentAsString(result).contains("Address line 2 (optional)") shouldBe true withClue ("HTML content did not contain 2nd input")
      contentAsString(result).contains("Town or city") shouldBe true withClue ("HTML content did not contain 3rd input")
      contentAsString(result).contains("County (optional)") shouldBe true withClue ("HTML content did not contain 4th input")
      contentAsString(result).contains("Postcode") shouldBe true withClue ("HTML content did not contain 5th input")
      contentAsString(result).contains("<title>") shouldBe true
    }

    "succeed and check for label, hintText, multichoice question" in new Setup {
      SubmissionServiceMock.Fetch.thenReturns(aSubmission.withIncompleteProgress())

      val formSubmissionLink = s"${aSubmission.id.value}/question/${OrganisationDetails.questionMultiple.id.value}"
      val result             = controller.showQuestion(aSubmission.id, OrganisationDetails.questionMultiple.id)(loggedInRequest.withCSRFToken)

      status(result) shouldBe OK
      contentAsString(result).contains(formSubmissionLink) shouldBe true withClue (s"(HTML content did not contain $formSubmissionLink)")
      contentAsString(result).contains("What is your favourite Colour?") shouldBe true withClue ("HTML content did not contain label")
      contentAsString(result).contains("Red") shouldBe true withClue ("HTML content did not check boxes")
      contentAsString(result).contains("<title>") shouldBe true
    }

    "succeed and check for label, hintText, confirm company name question" in new Setup {
      SubmissionServiceMock.Fetch.thenReturns(aSubmission.withIncompleteProgress())

      val formSubmissionLink = s"${aSubmission.id.value}/question/${OrganisationDetails.questionLtdOrgName.id.value}"
      val result             = controller.showQuestion(aSubmission.id, OrganisationDetails.questionLtdOrgName.id)(loggedInRequest.withCSRFToken)

      status(result) shouldBe OK
      contentAsString(result).contains(formSubmissionLink) shouldBe true withClue (s"(HTML content did not contain $formSubmissionLink)")
      contentAsString(result).contains("Is this your company?") shouldBe true withClue ("HTML content did not contain label")
      contentAsString(result).contains("<title>") shouldBe true
    }

    "succeed and check for label, hintText, confirm company address question" in new Setup {
      SubmissionServiceMock.Fetch.thenReturns(aSubmission.withIncompleteProgress())

      val formSubmissionLink = s"${aSubmission.id.value}/question/${OrganisationDetails.questionLtdOrgAddress.id.value}"
      val result             = controller.showQuestion(aSubmission.id, OrganisationDetails.questionLtdOrgAddress.id)(loggedInRequest.withCSRFToken)

      status(result) shouldBe OK
      contentAsString(result).contains(formSubmissionLink) shouldBe true withClue (s"(HTML content did not contain $formSubmissionLink)")
      contentAsString(result).contains("Is this the correct registered address for your company?") shouldBe true withClue ("HTML content did not contain label")
      contentAsString(result).contains("<title>") shouldBe true
    }

    "succeed and check for label, hintText, acknowledgement question" in new Setup {
      SubmissionServiceMock.Fetch.thenReturns(aSubmission.withIncompleteProgress())

      val formSubmissionLink = s"${aSubmission.id.value}/question/${OrganisationDetails.questionAcknowledgement.id.value}"
      val result             = controller.showQuestion(aSubmission.id, OrganisationDetails.questionAcknowledgement.id)(loggedInRequest.withCSRFToken)

      status(result) shouldBe OK
      contentAsString(result).contains(formSubmissionLink) shouldBe true withClue (s"(HTML content did not contain $formSubmissionLink)")
      contentAsString(
        result
      ).contains(
        "Your customers will see the information you provide here when they authorise your software to interact with HMRC."
      ) shouldBe true withClue ("HTML content did not contain statement")
      contentAsString(result).contains("Customers authorising your software") shouldBe true withClue ("HTML content did not contain label")
      contentAsString(result).contains("<title>") shouldBe true
    }

    "succeed and check for label, hintText, attachment question" in new Setup {
      val upscanResponse     = upscanInitiateResponse(OrganisationDetails.questionNonUkWithoutAttachment.id, aSubmission.id)
      SubmissionServiceMock.Fetch.thenReturns(aSubmission.withIncompleteProgress())
      UpscanInitiateConnectorMock.Initiate.succeedsWith(OrganisationDetails.questionNonUkWithoutAttachment.id, aSubmission.id)(upscanResponse)
      val expectedHtmlAction = upscanResponse.postTarget.replace("&", "&amp;")
      val result             = controller.showQuestion(aSubmission.id, OrganisationDetails.questionNonUkWithoutAttachment.id)(loggedInRequest.withCSRFToken)

      status(result) shouldBe OK

      contentAsString(result).contains(expectedHtmlAction) shouldBe true withClue (s"(HTML content did not contain $expectedHtmlAction)")
      contentAsString(result).contains("Attach the tax document") shouldBe true withClue ("HTML content did not contain label")
      contentAsString(result).contains(
        "You can upload your registration document as a scanned copy or photo of the original. The selected file must be smaller than 10MB."
      ) shouldBe true withClue ("HTML content did not contain hintText")
      contentAsString(result).contains("<title>") shouldBe true
      contentAsString(result).contains("Enter organisation details") shouldBe true withClue ("HTML content did not contain questionnaire name")
    }

    "display fail and show error in title when applicable" in new Setup {
      SubmissionServiceMock.Fetch.thenReturns(aSubmission.withIncompleteProgress())

      val result =
        controller.showQuestion(aSubmission.id, aSubmission.getQuestionOfInterest("organisationTypeId").get, None, Some(ValidationErrors(ValidationError(message = "blah"))))(
          loggedInRequest.withCSRFToken
        )

      status(result) shouldBe BAD_REQUEST
      contentAsString(result).contains("<title>Error:") shouldBe true withClue ("Page title should contain `Error: ` prefix")
      contentAsString(result).contains("blah") shouldBe true withClue ("Page should contain `blah` message")
    }

    "fail with a BAD REQUEST for an invalid questionId" in new Setup {
      SubmissionServiceMock.Fetch.thenReturns(aSubmission.withIncompleteProgress())

      val result = controller.showQuestion(aSubmission.id, Question.Id("BAD_ID"))(loggedInRequest.withCSRFToken)

      status(result) shouldBe BAD_REQUEST
    }

    "fail with NOT_FOUND if logged in user doesn't match submission user" in new Setup {
      SubmissionServiceMock.Fetch.thenReturnsWrongUser(aSubmission.withIncompleteProgress())

      val result = controller.showQuestion(aSubmission.id, questionId)(loggedInRequest.withCSRFToken)

      status(result) shouldBe NOT_FOUND
    }
  }

  "updateQuestion" should {
    "succeed" in new Setup {
      SubmissionServiceMock.Fetch.thenReturns(aSubmission.withIncompleteProgress())

      val formSubmissionLink = s"${aSubmission.id.value}/question/${questionId.value}/update"
      val result             = controller.updateQuestion(aSubmission.id, questionId)(loggedInRequest.withCSRFToken)

      status(result) shouldBe OK
      contentAsString(result).contains(formSubmissionLink) shouldBe true withClue (s"(HTML content did not contain $formSubmissionLink)")
    }

    "fail with a BAD REQUEST for an invalid questionId" in new Setup {
      SubmissionServiceMock.Fetch.thenReturns(aSubmission.withIncompleteProgress())

      val result = controller.updateQuestion(aSubmission.id, Question.Id("BAD_ID"))(loggedInRequest.withCSRFToken)

      status(result) shouldBe BAD_REQUEST
    }

    "fail with NOT FOUND if logged in user doesn't match submission user" in new Setup {
      SubmissionServiceMock.Fetch.thenReturnsWrongUser(aSubmission.withIncompleteProgress())

      val result = controller.updateQuestion(aSubmission.id, questionId)(loggedInRequest.withCSRFToken)

      status(result) shouldBe NOT_FOUND
    }

    "updating from section summary" should {
      "return to section summary when no more questions to answer" in new Setup {
        val fullyAnsweredSubmission = Submission.create(
          "bob@example.com",
          SubmissionId.random,
          Some(organisationId),
          instant,
          userId,
          testGroups,
          testQuestionIdsOfInterest,
          standardContext
        )
          .hasCompletelyAnsweredWith(samplePassAnswersToQuestions)
          .withCompletedProgress()

        SubmissionServiceMock.Fetch.thenReturns(fullyAnsweredSubmission)
        SubmissionServiceMock.RecordAnswer.thenReturns(fullyAnsweredSubmission)

        private val answer1 = "Updated answer"
        private val request = loggedInRequest
          .withFormUrlEncodedBody(
            Question.answerKey -> answer1,
            "submit-action"    -> "save",
            "returnTo"         -> "section-summary"
          )

        val result = controller.updateAnswer(fullyAnsweredSubmission.submission.id, questionId)(request.withCSRFToken)

        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(
          s"/api-platform-organisation/submission/${fullyAnsweredSubmission.submission.id.value}/questionnaire/${OrganisationDetails.questionnaire.id.value}/summary"
        )
      }
    }
  }

  "recordAnswer" should {
    "succeed when answer given" in new Setup {
      SubmissionServiceMock.Fetch.thenReturns(aSubmission.withIncompleteProgress())
      SubmissionServiceMock.RecordAnswer.thenReturns(aSubmission.withIncompleteProgress())
      private val answer1 = "Bobs Burgers"
      private val request = loggedInRequest.withFormUrlEncodedBody(Question.answerKey -> answer1, "submit-action" -> "save")

      val result = controller.recordAnswer(aSubmission.id, OrganisationDetails.questionLtdOrgName.id)(request.withCSRFToken)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(s"/api-platform-organisation/submission/${aSubmission.id.value}/question/${OrganisationDetails.questionLtdOrgAddress.id.value}")
    }

    "succeed when answer given and trim answer" in new Setup {
      private val answer1 = "  Bob's application  "
      private val request = loggedInRequest.withFormUrlEncodedBody(Question.answerKey -> answer1, "submit-action" -> "save")

      SubmissionServiceMock.Fetch.thenReturns(aSubmission.withIncompleteProgress())
      SubmissionServiceMock.RecordAnswer.thenReturnsForAnswer(Map(Question.answerKey -> Seq(answer1.trim()), "submit-action" -> Seq("save")), aSubmission.withIncompleteProgress())

      val result = controller.recordAnswer(aSubmission.id, OrganisationDetails.questionLtdOrgName.id)(request.withCSRFToken)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(s"/api-platform-organisation/submission/${aSubmission.id.value}/question/${OrganisationDetails.questionLtdOrgAddress.id.value}")
    }

    "succeed when empty answer given" in new Setup {
      private val answer1 = "   "
      private val request = loggedInRequest.withFormUrlEncodedBody(Question.answerKey -> answer1, "submit-action" -> "save")

      SubmissionServiceMock.Fetch.thenReturns(aSubmission.withIncompleteProgress())
      SubmissionServiceMock.RecordAnswer.thenReturnsForAnswer(Map(Question.answerKey -> Seq.empty, "submit-action" -> Seq("save")), aSubmission.withIncompleteProgress())

      val result = controller.recordAnswer(aSubmission.id, OrganisationDetails.questionLtdOrgName.id)(request.withCSRFToken)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(s"/api-platform-organisation/submission/${aSubmission.id.value}/question/${OrganisationDetails.questionLtdOrgAddress.id.value}")
    }

    "fail if invalid answer provided and returns downstream error" in new Setup {
      SubmissionServiceMock.Fetch.thenReturns(aSubmission.withIncompleteProgress())
      SubmissionServiceMock.RecordAnswer.thenReturnsError("Failed to record answer for submission")
      private val invalidEmailAnswer = "bob"
      private val request            = loggedInRequest.withFormUrlEncodedBody(Question.answerKey -> invalidEmailAnswer, "submit-action" -> "save")

      val result = controller.recordAnswer(aSubmission.id, ResponsibleIndividualDetails.question2.id)(request.withCSRFToken)

      status(result) shouldBe BAD_REQUEST

      val body = contentAsString(result)

      body should include("Failed to record answer for submission")
    }

    "fail if invalid address answer provided and returns downstream error" in new Setup {
      SubmissionServiceMock.Fetch.thenReturns(aSubmission.withIncompleteProgress())
      SubmissionServiceMock.RecordAnswer.thenReturnsError("Town or City required")
      private val request = loggedInRequest.withFormUrlEncodedBody("addressLineOne" -> "123 High Street", "postcode" -> "NW1 3PQ", "submit-action" -> "save")

      val result = controller.recordAnswer(aSubmission.id, OrganisationDetails.questionAddress.id)(request.withCSRFToken)

      status(result) shouldBe BAD_REQUEST

      val body = contentAsString(result)

      body should include("Town or City required")
    }

    "fail if invalid company number provided and returns downstream error" in new Setup {
      SubmissionServiceMock.Fetch.thenReturns(aSubmission.withIncompleteProgress())
      SubmissionServiceMock.RecordAnswer.thenReturnsError("The company number entered is not valid")
      private val invalidCompanyNumber = "not-a-number"
      private val request              = loggedInRequest.withFormUrlEncodedBody(Question.answerKey -> invalidCompanyNumber, "submit-action" -> "save")

      val result = controller.recordAnswer(aSubmission.id, OrganisationDetails.questionCompanyNumber.id)(request.withCSRFToken)

      status(result) shouldBe BAD_REQUEST

      val body = contentAsString(result)

      body should include("The company number entered is not valid")
    }

    "fail and redirect to the company number not found page when the company number lookup fails" in new Setup {
      SubmissionServiceMock.Fetch.thenReturns(aSubmission.withIncompleteProgress())
      SubmissionServiceMock.RecordAnswer.thenReturnsErrorWithKey(ValidationError.companyNumberNotFoundKey, "The company number 12345678 was not found")
      private val invalidCompanyNumber = "12345678"
      private val request              = loggedInRequest.withFormUrlEncodedBody(Question.answerKey -> invalidCompanyNumber, "submit-action" -> "save")

      val result = controller.recordAnswer(aSubmission.id, OrganisationDetails.questionCompanyNumber.id)(request.withCSRFToken)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(
        s"/api-platform-organisation/registration/company-number-not-found/${aSubmission.id.value}/${OrganisationDetails.questionCompanyNumber.id.value}"
      )
    }

    "completing last question in section" should {
      "redirect to section summary instead of checklist" in new Setup {
        val submissionWithOneQuestionLeft = aSubmission.withIncompleteProgress()

        SubmissionServiceMock.Fetch.thenReturns(submissionWithOneQuestionLeft)
        SubmissionServiceMock.RecordAnswer.thenReturns(submissionWithOneQuestionLeft.copy(
          questionnaireProgress = Map(OrganisationDetails.questionnaire.id ->
            QuestionnaireProgress(QuestionnaireState.Completed, List.empty))
        ))

        private val answer  = "Bob's Burgers"
        private val request = loggedInRequest.withFormUrlEncodedBody(Question.answerKey -> answer, "submit-action" -> "save")

        val result = controller.recordAnswer(aSubmission.id, OrganisationDetails.questionLtdOrgAddress.id)(request.withCSRFToken)

        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(s"/api-platform-organisation/submission/${aSubmission.id.value}/questionnaire/${OrganisationDetails.questionnaire.id.value}/summary")
      }
    }

  }

  "updateAnswer" should {
    "succeed when given an answer and redirect to check answers page if no more questions need answering" in new Setup {
      val fullyAnsweredSubmission = Submission.create(
        "bob@example.com",
        SubmissionId.random,
        Some(organisationId),
        instant,
        userId,
        testGroups,
        testQuestionIdsOfInterest,
        standardContext
      )
        .hasCompletelyAnsweredWith(samplePassAnswersToQuestions)
        .withCompletedProgress()

      SubmissionServiceMock.Fetch.thenReturns(fullyAnsweredSubmission)
      SubmissionServiceMock.RecordAnswer.thenReturns(fullyAnsweredSubmission)

      private val answer1 = "Yes"
      private val request = loggedInRequest.withFormUrlEncodedBody(Question.answerKey -> answer1, "submit-action" -> "save")

      val result = controller.updateAnswer(fullyAnsweredSubmission.submission.id, questionId)(request.withCSRFToken)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(s"/api-platform-organisation/submission/${fullyAnsweredSubmission.submission.id}/check-answers")
    }

    "succeed when given an answer and redirect to summary page if no more questions need answering" in new Setup {
      val fullyAnsweredSubmission = Submission.create(
        "bob@example.com",
        SubmissionId.random,
        Some(organisationId),
        instant,
        userId,
        testGroups,
        testQuestionIdsOfInterest,
        standardContext
      )
        .hasCompletelyAnsweredWith(samplePassAnswersToQuestions)
        .withCompletedProgress()

      SubmissionServiceMock.Fetch.thenReturns(fullyAnsweredSubmission)
      SubmissionServiceMock.RecordAnswer.thenReturns(fullyAnsweredSubmission)

      private val answer1 = "Yes"
      private val request = loggedInRequest.withFormUrlEncodedBody(Question.answerKey -> answer1, "returnTo" -> "section-summary", "submit-action" -> "save")

      val result = controller.updateAnswer(fullyAnsweredSubmission.submission.id, questionId)(request.withCSRFToken)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(s"/api-platform-organisation/submission/${fullyAnsweredSubmission.submission.id}/questionnaire/${questionnaireId.value}/summary")
    }

    "succeed when given no answer and redirect to check answers page if no more questions need answering" in new Setup {
      val fullyAnsweredSubmission = Submission.create(
        "bob@example.com",
        SubmissionId.random,
        Some(organisationId),
        instant,
        userId,
        testGroups,
        testQuestionIdsOfInterest,
        standardContext
      )
        .hasCompletelyAnsweredWith(samplePassAnswersToQuestions)
        .withCompletedProgress()

      SubmissionServiceMock.Fetch.thenReturns(fullyAnsweredSubmission)
      SubmissionServiceMock.RecordAnswer.thenReturns(fullyAnsweredSubmission)

      private val request = loggedInRequest.withFormUrlEncodedBody("submit-action" -> "save")

      val result = controller.updateAnswer(fullyAnsweredSubmission.submission.id, questionId)(request.withCSRFToken)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(s"/api-platform-organisation/submission/${fullyAnsweredSubmission.submission.id}/check-answers")
    }

    "succeed when given an answer and redirect to the next question to answer" in new Setup {
      val fullyAnsweredSubmission = Submission.create(
        "bob@example.com",
        SubmissionId.random,
        Some(organisationId),
        instant,
        userId,
        testGroups,
        testQuestionIdsOfInterest,
        standardContext
      )
        .hasCompletelyAnsweredWith(samplePassAnswersToQuestions)
        .withCompletedProgress()

      val modifiedAnswersToQuestions = fullyAnsweredSubmission.submission.latestInstance.answersToQuestions -
        OrganisationDetails.questionLtdOrgAddress.id ++ Map(
          OrganisationDetails.questionOrgType.id -> ActualAnswer.SingleChoiceAnswer("Partnership")
        )

      val modifiedProgress = Map(OrganisationDetails.questionnaire.id ->
        QuestionnaireProgress(
          QuestionnaireState.InProgress,
          List(
            OrganisationDetails.questionOrgType.id,
            OrganisationDetails.questionPartnershipType.id
          )
        ))

      val modifiedSubmission: ExtendedSubmission = fullyAnsweredSubmission.copy(
        submission = fullyAnsweredSubmission.submission.copy(
          instances = NonEmptyList(
            fullyAnsweredSubmission.submission.latestInstance.copy(
              answersToQuestions = modifiedAnswersToQuestions
            ),
            Nil
          )
        ),
        questionnaireProgress = fullyAnsweredSubmission.questionnaireProgress ++ modifiedProgress
      )

      SubmissionServiceMock.Fetch.thenReturns(fullyAnsweredSubmission)
      SubmissionServiceMock.RecordAnswer.thenReturns(modifiedSubmission)

      private val answer  = "Partnership"
      private val request = loggedInRequest.withFormUrlEncodedBody(Question.answerKey -> answer, "submit-action" -> "save")

      private val firstQuestionId    = OrganisationDetails.questionOrgType.id
      private val followUpQuestionId = OrganisationDetails.questionPartnershipType.id

      val result = controller.updateAnswer(fullyAnsweredSubmission.submission.id, firstQuestionId)(request.withCSRFToken)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(s"/api-platform-organisation/submission/${fullyAnsweredSubmission.submission.id.value}/question/${followUpQuestionId.value}/update")
    }
  }

  "PossibleAnswer.htmlValue" should {
    "return no spaces" in {
      val htmlValue = PossibleAnswer("something with spaces").htmlValue
      htmlValue.contains(" ") shouldBe false
    }

    "return hyphens instead of spaces" in {
      val htmlValue = PossibleAnswer("something with spaces").htmlValue
      htmlValue shouldBe "something-with-spaces"
    }

    "remove extraneous characters" in {
      val htmlValue = PossibleAnswer("something#hashed").htmlValue
      htmlValue.contains("#") shouldBe false
      htmlValue shouldBe "somethinghashed"
    }
  }
}
