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
import scala.jdk.CollectionConverters.*

import org.jsoup.Jsoup
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
import uk.gov.hmrc.apiplatform.modules.organisations.submissions.domain.models.QuestionnaireState.{Completed, InProgress}
import uk.gov.hmrc.apiplatform.modules.organisations.submissions.utils.SubmissionsTestData
import uk.gov.hmrc.apiplatform.modules.tpd.core.domain.models.User
import uk.gov.hmrc.apiplatform.modules.tpd.test.builders.UserBuilder
import uk.gov.hmrc.apiplatform.modules.tpd.test.utils.LocalUserIdTracker
import uk.gov.hmrc.apiplatformorganisationfrontend.WithLoggedInSession.*
import uk.gov.hmrc.apiplatformorganisationfrontend.config.{AppConfig, ErrorHandler}
import uk.gov.hmrc.apiplatformorganisationfrontend.controllers.*
import uk.gov.hmrc.apiplatformorganisationfrontend.mocks.connectors.ThirdPartyDeveloperConnectorMockModule
import uk.gov.hmrc.apiplatformorganisationfrontend.mocks.services.{OrganisationActionServiceMockModule, SubmissionServiceMockModule}
import uk.gov.hmrc.apiplatformorganisationfrontend.views.html.{CheckAnswersView, SectionSummaryView, SubmitSubmissionSuccessPage, SubmittedAnswersView}
import uk.gov.hmrc.apiplatformorganisationfrontend.{AsIdsHelpers, WithCSRFAddToken}

class CheckAnswersControllerSpec
    extends HmrcSpec
    with GuiceOneAppPerSuite
    with WithCSRFAddToken
    with UserBuilder
    with LocalUserIdTracker
    with SubmissionsTestData {

  trait HasSessionDeveloperFlow {
    val sessionParams = Seq("csrfToken" -> app.injector.instanceOf[CSRF.TokenProvider].generateToken)
  }

  override def fakeApplication(): Application = new GuiceApplicationBuilder().build()

  trait Setup
      extends SubmissionServiceMockModule
      with HasSessionDeveloperFlow
      with ThirdPartyDeveloperConnectorMockModule
      with OrganisationActionServiceMockModule
      with SubmissionsTestData
      with FixedClock
      with AsIdsHelpers
      with LocalUserIdTracker {

    implicit val hc: HeaderCarrier = HeaderCarrier()

    val completedProgress           = List(OrganisationDetails.questionnaire, ResponsibleIndividualDetails.questionnaire)
      .map(q => q.id -> QuestionnaireProgress(Completed, q.questions.asIds())).toMap
    val completedExtendedSubmission = ExtendedSubmission(aSubmission, completedProgress)

    val incompleteProgress           = List(OrganisationDetails.questionnaire, ResponsibleIndividualDetails.questionnaire)
      .map(q => q.id -> QuestionnaireProgress(InProgress, q.questions.asIds())).toMap
    val incompleteExtendedSubmission = ExtendedSubmission(aSubmission, incompleteProgress)

    val checkAnswersView              = app.injector.instanceOf[CheckAnswersView]
    val submittedAnswersView          = app.injector.instanceOf[SubmittedAnswersView]
    val submitSubmissionSuccessPage   = app.injector.instanceOf[SubmitSubmissionSuccessPage]
    val sectionSummaryView            = app.injector.instanceOf[SectionSummaryView]
    val mcc                           = app.injector.instanceOf[MessagesControllerComponents]
    val cookieSigner                  = app.injector.instanceOf[CookieSigner]
    val errorHandler                  = app.injector.instanceOf[ErrorHandler]
    implicit val appConfig: AppConfig = app.injector.instanceOf[AppConfig]

    val controller = new CheckAnswersController(
      errorHandler,
      mcc,
      cookieSigner,
      SubmissionServiceMock.aMock,
      OrganisationActionServiceMock.aMock,
      checkAnswersView,
      submittedAnswersView,
      submitSubmissionSuccessPage,
      sectionSummaryView,
      ThirdPartyDeveloperConnectorMock.aMock
    )

    val loggedInRequest             = FakeRequest().withUser(controller)(sessionId).withSession(sessionParams*)
    implicit val loggedInUser: User = user

    ThirdPartyDeveloperConnectorMock.FetchSession.succeeds()
  }

  "checkAnswersPage" should {
    "succeed when submission is complete" in new Setup {
      SubmissionServiceMock.Fetch.thenReturns(buildFullyAnsweredSubmission().withCompletedProgress())

      val result = controller.checkAnswersPage(submissionId)(loggedInRequest.withCSRFToken)

      status(result) shouldBe OK
      contentAsString(result) should include("Confirm and send")
    }

    "Flash an error when submission is failed" in new Setup {
      SubmissionServiceMock.Fetch.thenReturns(buildFullyAnsweredSubmission().withCompletedProgress())

      val result = controller.checkAnswersPage(submissionId)(loggedInRequest.withCSRFToken.withFlash("error" -> "some submit error"))

      status(result) shouldBe OK
      contentAsString(result) should include("some submit error")
    }

    "return an error when submission is not found" in new Setup {
      SubmissionServiceMock.Fetch.thenReturnsNone()

      val result = controller.checkAnswersPage(submissionId)(loggedInRequest.withCSRFToken)

      status(result) shouldBe NOT_FOUND
    }

    "return read only version of page when answers have been submitted" in new Setup {
      SubmissionServiceMock.Fetch.thenReturns(answeredSubmission.withSubmittedProgress())

      val result = controller.checkAnswersPage(submissionId)(loggedInRequest.withCSRFToken)

      status(result) shouldBe OK
      contentAsString(result) should include("Your answers")
      contentAsString(result) should include("to update submitted answers")
    }

    "fail with NOT_FOUND if logged in user doesn't match submission user" in new Setup {
      SubmissionServiceMock.Fetch.thenReturnsWrongUser(answeredSubmission.withCompletedProgress())

      val result = controller.checkAnswersPage(submissionId)(loggedInRequest.withCSRFToken)

      status(result) shouldBe NOT_FOUND
    }

    "show the company name after company lookup when the confirm company name question is answered yes" in new Setup {
      val companyDetails = Submission.CompanyDetails("12345678", "Easysoft Ltd")
      val submission     = Submission.updateLatestAdditionalDataTo(Some(Submission.AdditionalData(Some(companyDetails))))(
        aSubmission.hasCompletelyAnsweredWith(Map(OrganisationDetails.questionLtdOrgName.id -> ActualAnswer.SingleChoiceAnswer("Yes")))
      )
      SubmissionServiceMock.Fetch.thenReturns(submission.withCompletedProgress())

      val result = controller.checkAnswersPage(submissionId)(loggedInRequest.withCSRFToken)

      status(result) shouldBe OK
      summaryLinesFor(OrganisationDetails.questionLtdOrgName)(contentAsString(result)) shouldBe Seq("Easysoft Ltd")
    }

    "show 'Not confirmed' after company lookup when the confirm company name question is answered no" in new Setup {
      val submission = aSubmission.hasCompletelyAnsweredWith(Map(OrganisationDetails.questionLtdOrgName.id -> ActualAnswer.SingleChoiceAnswer("No")))
      SubmissionServiceMock.Fetch.thenReturns(submission.withCompletedProgress())

      val result = controller.checkAnswersPage(submissionId)(loggedInRequest.withCSRFToken)

      status(result) shouldBe OK
      summaryLinesFor(OrganisationDetails.questionLtdOrgName)(contentAsString(result)) shouldBe Seq("Not confirmed")
    }

    "show the company address with all fields after company lookup when the confirm company address question is answered yes" in new Setup {
      val companyDetails = Submission.CompanyDetails(
        companyNumber = "12345678",
        companyName = "Easysoft Ltd",
        addressLineOne = Some("1 High Street"),
        addressLineTwo = Some("Suite 2"),
        careOf = Some("John Smith"),
        country = Some("United Kingdom"),
        locality = Some("London"),
        poBox = Some("PO123"),
        postalCode = Some("SW1A 1AA"),
        premises = Some("Unit 5"),
        region = Some("Greater London")
      )
      val submission     = Submission.updateLatestAdditionalDataTo(Some(Submission.AdditionalData(Some(companyDetails))))(
        aSubmission.hasCompletelyAnsweredWith(Map(OrganisationDetails.questionLtdOrgAddress.id -> ActualAnswer.SingleChoiceAnswer("Yes")))
      )
      SubmissionServiceMock.Fetch.thenReturns(submission.withCompletedProgress())

      val result = controller.checkAnswersPage(submissionId)(loggedInRequest.withCSRFToken)

      status(result) shouldBe OK
      summaryLinesFor(OrganisationDetails.questionLtdOrgAddress)(contentAsString(result)) shouldBe Seq(
        "John Smith",
        "PO123",
        "Unit 5",
        "1 High Street",
        "Suite 2",
        "London",
        "Greater London",
        "SW1A 1AA",
        "United Kingdom"
      )
    }

    "show the company address after company lookup when the confirm company address question is answered no" in new Setup {
      val companyDetails = Submission.CompanyDetails(
        companyNumber = "12345678",
        companyName = "Easysoft Ltd",
        addressLineOne = Some("1 High Street")
      )
      val submission     = Submission.updateLatestAdditionalDataTo(Some(Submission.AdditionalData(Some(companyDetails))))(
        aSubmission.hasCompletelyAnsweredWith(Map(OrganisationDetails.questionLtdOrgAddress.id -> ActualAnswer.SingleChoiceAnswer("No")))
      )
      SubmissionServiceMock.Fetch.thenReturns(submission.withCompletedProgress())

      val result = controller.checkAnswersPage(submissionId)(loggedInRequest.withCSRFToken)

      status(result) shouldBe OK
      summaryLinesFor(OrganisationDetails.questionLtdOrgAddress)(contentAsString(result)) shouldBe Seq("1 High Street")
    }

    "show n/a when the company address question has been confirmed but no address fields are populated" in new Setup {
      val companyDetails = Submission.CompanyDetails("12345678", "Acme Ltd")
      val submission     = Submission.updateLatestAdditionalDataTo(Some(Submission.AdditionalData(Some(companyDetails))))(
        aSubmission.hasCompletelyAnsweredWith(Map(OrganisationDetails.questionLtdOrgAddress.id -> ActualAnswer.SingleChoiceAnswer("Yes")))
      )
      SubmissionServiceMock.Fetch.thenReturns(submission.withCompletedProgress())

      val result = controller.checkAnswersPage(submissionId)(loggedInRequest.withCSRFToken)

      status(result) shouldBe OK
      summaryLinesFor(OrganisationDetails.questionLtdOrgAddress)(contentAsString(result)) shouldBe Seq("n/a")
    }

    "fall back to n/a without throwing when companyDetails is absent" in new Setup {
      val submission = aSubmission.hasCompletelyAnsweredWith(Map(OrganisationDetails.questionLtdOrgName.id -> ActualAnswer.SingleChoiceAnswer("Yes")))
      SubmissionServiceMock.Fetch.thenReturns(submission.withCompletedProgress())

      val result = controller.checkAnswersPage(submissionId)(loggedInRequest.withCSRFToken)

      status(result) shouldBe OK
      summaryLinesFor(OrganisationDetails.questionLtdOrgName)(contentAsString(result)) shouldBe Seq("n/a")
    }

    "show all manual entry questions when the submission is fully answered" in new Setup {
      val submission = aSubmission.hasCompletelyAnsweredWith(samplePassAnswersToQuestions)
      SubmissionServiceMock.Fetch.thenReturns(submission.withCompletedProgress())

      val result = controller.checkAnswersPage(submissionId)(loggedInRequest.withCSRFToken)

      status(result) shouldBe OK
      val content = contentAsString(result)
      summaryLinesFor(OrganisationDetails.questionOrgType)(content) shouldBe Seq(answerInFixtureFor(OrganisationDetails.questionOrgType))
      summaryLinesFor(OrganisationDetails.questionCompanyNumber)(content) shouldBe Seq(answerInFixtureFor(OrganisationDetails.questionCompanyNumber))
      summaryLinesFor(OrganisationDetails.questionLtdOrgUtr)(content) shouldBe Seq(answerInFixtureFor(OrganisationDetails.questionLtdOrgUtr))
      summaryLinesFor(ResponsibleIndividualDetails.question1)(content) shouldBe Seq(answerInFixtureFor(ResponsibleIndividualDetails.question1))
      summaryLinesFor(ResponsibleIndividualDetails.question2)(content) shouldBe Seq(answerInFixtureFor(ResponsibleIndividualDetails.question2))
      summaryLinesFor(ResponsibleIndividualDetails.question3)(content) shouldBe Seq(answerInFixtureFor(ResponsibleIndividualDetails.question3))
      summaryLinesFor(ResponsibleIndividualDetails.question4)(content) shouldBe Seq(answerInFixtureFor(ResponsibleIndividualDetails.question4))
      summaryLinesFor(ResponsibleIndividualDetails.question5)(content) shouldBe Seq(answerInFixtureFor(ResponsibleIndividualDetails.question5))
      summaryLinesFor(ResponsibleIndividualDetails.question6)(content) shouldBe Seq(answerInFixtureFor(ResponsibleIndividualDetails.question6))
    }
  }

  "checkAnswersAction" should {
    "redirect to success page if successful" in new Setup {
      SubmissionServiceMock.Fetch.thenReturns(answeringSubmission.withIncompleteProgress())
      SubmissionServiceMock.SubmitSubmission.thenReturns(submittedSubmission)

      val result = controller.checkAnswersAction(submissionId)(loggedInRequest.withCSRFToken)
      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(s"/api-platform-organisation/submission/$submissionId/submit-success")
    }

    "redirect back to check answers page if fails" in new Setup {
      SubmissionServiceMock.Fetch.thenReturns(answeringSubmission.withIncompleteProgress())
      SubmissionServiceMock.SubmitSubmission.thenReturnsError()

      val result = controller.checkAnswersAction(submissionId)(loggedInRequest.withCSRFToken)
      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(s"/api-platform-organisation/submission/$submissionId/check-answers")
    }
  }

  "submitSuccessPage" should {
    "show success page" in new Setup {
      SubmissionServiceMock.Fetch.thenReturns(answeringSubmission.withIncompleteProgress())

      val result = controller.submitSuccessPage(submissionId)(loggedInRequest.withCSRFToken)
      status(result) shouldBe OK
      contentAsString(result) should include("We’re checking the information you provided")
    }
  }

  "showSectionSummary" should {
    "display section summary with only one questionnaire" in new Setup {
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

      val result = controller.showSectionSummary(fullyAnsweredSubmission.submission.id, OrganisationDetails.questionnaire.id)(loggedInRequest.withCSRFToken)

      status(result) shouldBe OK
      val content = contentAsString(result)
      content should include("Check your answers")
    }

    "fail with BAD_REQUEST when questionnaire not found" in new Setup {
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

      val result = controller.showSectionSummary(fullyAnsweredSubmission.submission.id, Questionnaire.Id("invalid-id"))(loggedInRequest.withCSRFToken)

      status(result) shouldBe BAD_REQUEST
    }
  }

  "sectionSummaryAction" should {
    "redirect to checklist" in new Setup {
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

      val request = loggedInRequest.withCSRFToken
      val result  = controller.sectionSummaryAction(fullyAnsweredSubmission.submission.id)(request)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(s"/api-platform-organisation/submission/${fullyAnsweredSubmission.submission.id.value}/checklist")
    }
  }

  private def answerInFixtureFor(question: Question): String =
    samplePassAnswersToQuestions(question.id) match {
      case ActualAnswer.SingleChoiceAnswer(value)                                => value
      case ActualAnswer.TextAnswer(value)                                        => value
      case ActualAnswer.CompanyNumberAnswer(value)                               => value
      case ActualAnswer.NameAnswer(FullName(_, Some(firstName), Some(lastName))) => s"$firstName $lastName"
      case other                                                                 => fail(s"Failed to get value for: $other")
    }

  private def summaryLinesFor(question: Question)(html: String): Seq[String] = {
    val label = question.summary.getOrElse(question.wording.value)
    Jsoup.parse(html).select("div.govuk-summary-list__row").asScala
      .find(_.select("dt.govuk-summary-list__key").text == label)
      .map(_.select("dd.govuk-summary-list__value").html.split("<br>").toSeq.map(_.trim))
      .getOrElse(fail(s"No summary row labelled '$label'"))
  }
}
