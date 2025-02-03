/*
 * Copyright 2024 HM Revenue & Customs
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

package uk.gov.hmrc.apiplatform.modules.organisations.submissions.utils

import scala.collection.immutable.ListMap

import cats.data.NonEmptyList
import cats.implicits._

import uk.gov.hmrc.apiplatform.modules.organisations.submissions.domain.models._

trait QuestionnaireTestData {

  object OrganisationDetails {

    val questionOrgType = Question.ChooseOneOfQuestion(
      Question.Id("cbdf264f-be39-4638-92ff-6ecd2259c662"),
      Wording("What is your organisation type?"),
      statement = None,
      marking = ListMap(
        (PossibleAnswer("UK limited company")                                             -> Mark.Pass),
        (PossibleAnswer("Sole trader")                                                    -> Mark.Pass),
        (PossibleAnswer("Partnership")                                                    -> Mark.Pass),
        (PossibleAnswer("Registered society")                                             -> Mark.Pass),
        (PossibleAnswer("Charitable Incorporated Organisation (CIO)")                     -> Mark.Pass),
        (PossibleAnswer("Non-UK company with a branch or place of business in the UK")    -> Mark.Warn),
        (PossibleAnswer("Non-UK company without a branch or place of business in the UK") -> Mark.Fail)
      ),
      errorInfo = ErrorInfo("Select your organisation type").some
    )

    val questionPartnershipType = Question.ChooseOneOfQuestion(
      Question.Id("adba162f-be58-2818-92ff-2ecd1799c513"),
      Wording("What type of partnership are you registering?"),
      statement = None,
      marking = ListMap(
        (PossibleAnswer("General partnership")           -> Mark.Pass),
        (PossibleAnswer("Limited liability partnership") -> Mark.Pass),
        (PossibleAnswer("Limited partnership")           -> Mark.Pass),
        (PossibleAnswer("Scottish partnership")          -> Mark.Pass),
        (PossibleAnswer("Scottish limited partnership")  -> Mark.Pass)
      ),
      errorInfo = ErrorInfo("Select your partnership type").some
    )

    val questionCompanyNumber = Question.TextQuestion(
      Question.Id("4e148791-1a07-4f28-8fe4-ba3e18cdc118"),
      Wording("What is the company registration number?"),
      statement = Statement(
        CompoundFragment(
          StatementText("You can "),
          StatementLink("search Companies House for your company registration number (opens in new tab)", "https://find-and-update.company-information.service.gov.uk/"),
          StatementText(".")
        )
      ).some,
      hintText = StatementText("It is 8 characters. For example, 01234567 or AC012345.").some,
      absence = Tuple2("My organisation doesn't have a company registration", Mark.Fail).some,
      errorInfo = ErrorInfo("Your company registration number cannot be blank", "Enter your company registration number, like 01234567").some
    )

    val questionLtdOrgName = Question.TextQuestion(
      Question.Id("a2dbf1a7-e31b-4c89-a755-21f0652ca9cc"),
      Wording("What is your organisation’s name?"),
      statement = None,
      validation = TextValidation.OrganisationName.some,
      errorInfo = ErrorInfo("Your organsation name cannot be blank", "Enter your organisation name").some
    )

    val questionLtdOrgAddress = Question.TextQuestion(
      Question.Id("e1dbf1a3-e28b-1c83-a739-86f1319ca8cc"),
      Wording("What is your organisation’s address?"),
      statement = None,
      errorInfo = ErrorInfo("Your organsation address cannot be blank", "Enter your organisation address").some
    )

    val questionLtdOrgUtr = Question.TextQuestion(
      Question.Id("6be23951-ac69-47bf-aa56-86d3d690ee0b"),
      Wording("What is your Corporation Tax Unique Taxpayer Reference (UTR)?"),
      statement = Statement(
        CompoundFragment(
          StatementText("It will be on tax returns and other letters about Corporation Tax. It may be called ‘reference’, ‘UTR’ or ‘official use’. You can "),
          StatementLink("find a lost UTR number (opens in new tab)", "https://www.gov.uk/find-lost-utr-number"),
          StatementText(".")
        )
      ).some,
      hintText = StatementText("Your UTR can be 10 or 13 digits long.").some,
      errorInfo = ErrorInfo("Your Corporation Tax Unique Taxpayer Reference cannot be blank", "Enter your Corporation Tax Unique Taxpayer Reference, like 1234567890").some
    )

    val questionSoleName = Question.TextQuestion(
      Question.Id("d1abf9a7-e31b-4c89-a755-21f0652ca23a"),
      Wording("What is your organisation’s name?"),
      statement = None,
      validation = TextValidation.OrganisationName.some,
      errorInfo = ErrorInfo("Your organsation name cannot be blank", "Enter your organisation name").some
    )

    val questionRsOrgName = Question.TextQuestion(
      Question.Id("a2dbf9a7-e31b-4c89-a755-21f0652de91c"),
      Wording("What is your organisation’s name?"),
      statement = None,
      validation = TextValidation.OrganisationName.some,
      errorInfo = ErrorInfo("Your organsation name cannot be blank", "Enter your organisation name").some
    )

    val questionCioOrgName = Question.TextQuestion(
      Question.Id("d8aaf3a2-e31b-4c89-a755-21f0652ab52a"),
      Wording("What is your organisation’s name?"),
      statement = None,
      validation = TextValidation.OrganisationName.some,
      errorInfo = ErrorInfo("Your organsation name cannot be blank", "Enter your organisation name").some
    )

    val questionNonUkWithOrgName = Question.TextQuestion(
      Question.Id("b2fef9a2-e31b-4c89-a755-21f0652af99e"),
      Wording("What is your organisation’s name?"),
      statement = None,
      validation = TextValidation.OrganisationName.some,
      errorInfo = ErrorInfo("Your organsation name cannot be blank", "Enter your organisation name").some
    )

    val questionNonUkWithoutOrgName = Question.TextQuestion(
      Question.Id("a8eaf9a2-e31b-4c89-a755-21f0652ae45a"),
      Wording("What is your organisation’s name?"),
      statement = None,
      validation = TextValidation.OrganisationName.some,
      errorInfo = ErrorInfo("Your organsation name cannot be blank", "Enter your organisation name").some
    )

    val questionGpOrgName = Question.TextQuestion(
      Question.Id("b6dbf9a2-e31b-4c89-a755-23f0652ab29d"),
      Wording("What is your organisation’s name?"),
      statement = None,
      validation = TextValidation.OrganisationName.some,
      errorInfo = ErrorInfo("Your organsation name cannot be blank", "Enter your organisation name").some
    )

    val questionLlpOrgName = Question.TextQuestion(
      Question.Id("a1dbf9a2-e31b-4c89-a755-23f0652ab17a"),
      Wording("What is your organisation’s name?"),
      statement = None,
      validation = TextValidation.OrganisationName.some,
      errorInfo = ErrorInfo("Your organsation name cannot be blank", "Enter your organisation name").some
    )

    val questionLpOrgName = Question.TextQuestion(
      Question.Id("f3dbf9a2-e31b-4c89-a755-23f0652ab57e"),
      Wording("What is your organisation’s name?"),
      statement = None,
      validation = TextValidation.OrganisationName.some,
      errorInfo = ErrorInfo("Your organsation name cannot be blank", "Enter your organisation name").some
    )

    val questionSpOrgName = Question.TextQuestion(
      Question.Id("c7dbf9a2-e31b-4c89-a755-23f0651ab48a"),
      Wording("What is your organisation’s name?"),
      statement = None,
      validation = TextValidation.OrganisationName.some,
      errorInfo = ErrorInfo("Your organsation name cannot be blank", "Enter your organisation name").some
    )

    val questionSlpOrgName = Question.TextQuestion(
      Question.Id("e3dbf6a2-e31b-4c89-a755-23f0652ab17c"),
      Wording("What is your organisation’s name?"),
      statement = None,
      validation = TextValidation.OrganisationName.some,
      errorInfo = ErrorInfo("Your organsation name cannot be blank", "Enter your organisation name").some
    )

    val questionnaire = Questionnaire(
      id = Questionnaire.Id("ac69b129-524a-4d10-89a5-7bfa46ed95c7"),
      label = Questionnaire.Label("Enter organisation details"),
      questions = NonEmptyList.of(
        QuestionItem(questionOrgType),
        QuestionItem(questionCompanyNumber, AskWhen.AskWhenAnswer(questionOrgType, "UK limited company")),
        QuestionItem(questionLtdOrgName, AskWhen.AskWhenAnswer(questionOrgType, "UK limited company")),
        QuestionItem(questionLtdOrgAddress, AskWhen.AskWhenAnswer(questionOrgType, "UK limited company")),
        QuestionItem(questionLtdOrgUtr, AskWhen.AskWhenAnswer(questionOrgType, "UK limited company")),
        QuestionItem(questionSoleName, AskWhen.AskWhenAnswer(questionOrgType, "Sole trader")),
        QuestionItem(questionRsOrgName, AskWhen.AskWhenAnswer(questionOrgType, "Registered society")),
        QuestionItem(questionCioOrgName, AskWhen.AskWhenAnswer(questionOrgType, "Charitable Incorporated Organisation (CIO)")),
        QuestionItem(questionNonUkWithOrgName, AskWhen.AskWhenAnswer(questionOrgType, "Non-UK company with a branch or place of business in the UK")),
        QuestionItem(questionNonUkWithoutOrgName, AskWhen.AskWhenAnswer(questionOrgType, "Non-UK company without a branch or place of business in the UK")),
        QuestionItem(questionPartnershipType, AskWhen.AskWhenAnswer(questionOrgType, "Partnership")),
        QuestionItem(questionGpOrgName, AskWhen.AskWhenAnswer(questionPartnershipType, "General partnership")),
        QuestionItem(questionLlpOrgName, AskWhen.AskWhenAnswer(questionPartnershipType, "Limited liability partnership")),
        QuestionItem(questionLpOrgName, AskWhen.AskWhenAnswer(questionPartnershipType, "Limited partnership")),
        QuestionItem(questionSpOrgName, AskWhen.AskWhenAnswer(questionPartnershipType, "Scottish partnership")),
        QuestionItem(questionSlpOrgName, AskWhen.AskWhenAnswer(questionPartnershipType, "Scottish limited partnership"))
      )
    )
  }

  object ResponsibleIndividualDetails {

    val question1 = Question.YesNoQuestion(
      Question.Id("99d9362d-e365-4af1-aa46-88e95f9858f7"),
      Wording("Are you the individual responsible for the software in your organisation?"),
      statement = Statement(
        StatementText("As the responsible individual you:"),
        StatementBullets(
          CompoundFragment(
            StatementText("ensure your software conforms to the "),
            StatementLink("terms of use (opens in new tab)", "/api-documentation/docs/terms-of-use")
          ),
          CompoundFragment(
            StatementText("understand the "),
            StatementLink("consequences of not conforming to the terms of use (opens in new tab)", "/api-documentation/docs/terms-of-use")
          )
        )
      ).some,
      yesMarking = Mark.Pass,
      noMarking = Mark.Pass,
      errorInfo = ErrorInfo("Select Yes if you are the individual responsible for the software in your organisation").some
    )

    val question2 = Question.TextQuestion(
      Question.Id("36b7e670-83fc-4b31-8f85-4d3394908495"),
      Wording("Who is responsible for the software in your organisation?"),
      statement = None,
      label = Question.Label("First and last name").some,
      errorInfo = ErrorInfo("Enter a first and last name", "First and last name cannot be blank").some
    )

    val question3 = Question.TextQuestion(
      Question.Id("18b4e672-43fc-8b33-9f83-7d324908496"),
      Wording("What is the responsible individual's job title in your organisation?"),
      statement = None,
      label = Question.Label("Job title").some,
      errorInfo = ErrorInfo("Enter a job title", "Job title cannot be blank").some
    )

    val question4 = Question.TextQuestion(
      Question.Id("fb9b8036-cc88-4f4e-ad84-c02caa4cebae"),
      Wording("Give us the email address of the individual responsible for the software"),
      statement = Statement(
        StatementText("We will use this email to invite the responsible individual to create an account on the Developer Hub."),
        StatementText("The responsible individual must verify before your registration can be completed.")
      ).some,
      label = Question.Label("Email address").some,
      hintText = StatementText("Cannot be a shared mailbox").some,
      validation = TextValidation.Email.some,
      errorInfo = ErrorInfo("Enter an email address in the correct format, like yourname@example.com", "Email address cannot be blank").some
    )

    val question5 = Question.TextQuestion(
      Question.Id("a27b8039-cc32-4f2e-ad88-c96caa1cebae"),
      Wording("Give us the telephone number of the individual responsible for the software"),
      statement = Statement(
        StatementText("We'll only use this to contact you about your organisation's Developer Hub account.")
      ).some,
      label = Question.Label("Telephone").some,
      hintText = StatementText("You can enter an organisation, personal or extension number. Include the country code for international numbers.").some,
      errorInfo = ErrorInfo("Enter a telephone number", "Telephone number cannot be blank").some
    )

    val question6 = Question.TextQuestion(
      Question.Id("b2dbf6a1-e39b-4c38-a524-19f0854ca1cc"),
      Wording("What is your organisation’s website address?"),
      statement = None,
      hintText = StatementText("For example https://example.com").some,
      absence = ("My organisation doesn't have a website", Mark.Fail).some,
      validation = TextValidation.Url.some,
      errorInfo = ErrorInfo("Enter a website address in the correct format, like https://example.com", "Enter a URL in the correct format, like https://example.com").some
    )

    val questionnaire = Questionnaire(
      id = Questionnaire.Id("ac69b129-524a-4d10-89a5-7bfa46ed95c7"),
      label = Questionnaire.Label("Enter responsible individual details"),
      questions = NonEmptyList.of(
        QuestionItem(question1),
        QuestionItem(question2, AskWhen.AskWhenAnswer(question1, "No")),
        QuestionItem(question3),
        QuestionItem(question4, AskWhen.AskWhenAnswer(question1, "No")),
        QuestionItem(question5),
        QuestionItem(question6)
      )
    )
  }

  val testGroups =
    NonEmptyList.of(
      GroupOfQuestionnaires(
        heading = "About your organisation",
        links = NonEmptyList.of(
          OrganisationDetails.questionnaire,
          ResponsibleIndividualDetails.questionnaire
        )
      )
    )

  val testQuestionIdsOfInterest = QuestionIdsOfInterest(
    organisationTypeId = OrganisationDetails.questionOrgType.id,
    partnershipTypeId = OrganisationDetails.questionPartnershipType.id,
    organisationNameLtdId = OrganisationDetails.questionLtdOrgName.id,
    organisationNameSoleId = OrganisationDetails.questionSoleName.id,
    organisationNameRsId = OrganisationDetails.questionRsOrgName.id,
    organisationNameCioId = OrganisationDetails.questionCioOrgName.id,
    organisationNameNonUkWithId = OrganisationDetails.questionNonUkWithOrgName.id,
    organisationNameNonUkWithoutId = OrganisationDetails.questionNonUkWithoutOrgName.id,
    organisationNameGpId = OrganisationDetails.questionGpOrgName.id,
    organisationNameLlpId = OrganisationDetails.questionLlpOrgName.id,
    organisationNameLpId = OrganisationDetails.questionLpOrgName.id,
    organisationNameSpId = OrganisationDetails.questionSpOrgName.id,
    organisationNameSlpId = OrganisationDetails.questionSlpOrgName.id
  )

  val questionnaire      = ResponsibleIndividualDetails.questionnaire
  val questionnaireId    = questionnaire.id
  val question           = questionnaire.questions.head.question
  val questionId         = question.id
  val question2Id        = questionnaire.questions.tail.head.question.id
  val questionnaireAlt   = OrganisationDetails.questionnaire
  val questionnaireAltId = questionnaireAlt.id
  val questionAltId      = questionnaireAlt.questions.head.question.id
  val optionalQuestion   = ResponsibleIndividualDetails.question6
  val optionalQuestionId = optionalQuestion.id

  val allQuestionnaires = testGroups.flatMap(_.links)

  val expectedAppName = "expectedAppName"

  val answersToQuestions: Submission.AnswersToQuestions =
    Map(
      testQuestionIdsOfInterest.organisationTypeId -> ActualAnswer.SingleChoiceAnswer("UK limited company")
    )

  val samplePassAnswersToQuestions = Map(
    (OrganisationDetails.questionOrgType.id       -> ActualAnswer.SingleChoiceAnswer("UK limited company")),
    (OrganisationDetails.questionCompanyNumber.id -> ActualAnswer.TextAnswer("12345678")),
    (OrganisationDetails.questionLtdOrgName.id    -> ActualAnswer.TextAnswer("Bobs Burgers")),
    (OrganisationDetails.questionLtdOrgAddress.id -> ActualAnswer.TextAnswer("1 High Street, London")),
    (OrganisationDetails.questionLtdOrgUtr.id     -> ActualAnswer.TextAnswer("1234567890")),
    (ResponsibleIndividualDetails.question1.id    -> ActualAnswer.SingleChoiceAnswer("No")),
    (ResponsibleIndividualDetails.question2.id    -> ActualAnswer.TextAnswer("Bob Fleming")),
    (ResponsibleIndividualDetails.question3.id    -> ActualAnswer.TextAnswer("Managing Director")),
    (ResponsibleIndividualDetails.question4.id    -> ActualAnswer.TextAnswer("bob@burgers.com")),
    (ResponsibleIndividualDetails.question5.id    -> ActualAnswer.TextAnswer("01234 567890")),
    (ResponsibleIndividualDetails.question6.id    -> ActualAnswer.TextAnswer("https://www.bobsburgers.com"))
  )

  val sampleFailAnswersToQuestions = Map(
    (OrganisationDetails.questionOrgType.id       -> ActualAnswer.SingleChoiceAnswer("Non-UK company without a branch or place of business in the UK")),
    (OrganisationDetails.questionCompanyNumber.id -> ActualAnswer.TextAnswer("12345678")),
    (OrganisationDetails.questionLtdOrgName.id    -> ActualAnswer.TextAnswer("Bobs Burgers")),
    (OrganisationDetails.questionLtdOrgAddress.id -> ActualAnswer.TextAnswer("1 High Street, London")),
    (OrganisationDetails.questionLtdOrgUtr.id     -> ActualAnswer.TextAnswer("1234567890")),
    (ResponsibleIndividualDetails.question1.id    -> ActualAnswer.SingleChoiceAnswer("No")),
    (ResponsibleIndividualDetails.question2.id    -> ActualAnswer.TextAnswer("Bob Fleming")),
    (ResponsibleIndividualDetails.question3.id    -> ActualAnswer.TextAnswer("Managing Director")),
    (ResponsibleIndividualDetails.question4.id    -> ActualAnswer.TextAnswer("bob@burgers.com")),
    (ResponsibleIndividualDetails.question5.id    -> ActualAnswer.TextAnswer("01234 567890")),
    (ResponsibleIndividualDetails.question6.id    -> ActualAnswer.TextAnswer("https://www.bobsburgers.com"))
  )

  val sampleWarningsAnswersToQuestions = Map(
    (OrganisationDetails.questionOrgType.id       -> ActualAnswer.SingleChoiceAnswer("Non-UK company with a branch or place of business in the UK")),
    (OrganisationDetails.questionCompanyNumber.id -> ActualAnswer.TextAnswer("12345678")),
    (OrganisationDetails.questionLtdOrgName.id    -> ActualAnswer.TextAnswer("Bobs Burgers")),
    (OrganisationDetails.questionLtdOrgAddress.id -> ActualAnswer.TextAnswer("1 High Street, London")),
    (OrganisationDetails.questionLtdOrgUtr.id     -> ActualAnswer.TextAnswer("1234567890")),
    (ResponsibleIndividualDetails.question1.id    -> ActualAnswer.SingleChoiceAnswer("No")),
    (ResponsibleIndividualDetails.question2.id    -> ActualAnswer.TextAnswer("Bob Fleming")),
    (ResponsibleIndividualDetails.question3.id    -> ActualAnswer.TextAnswer("Managing Director")),
    (ResponsibleIndividualDetails.question4.id    -> ActualAnswer.TextAnswer("bob@burgers.com")),
    (ResponsibleIndividualDetails.question5.id    -> ActualAnswer.TextAnswer("01234 567890")),
    (ResponsibleIndividualDetails.question6.id    -> ActualAnswer.TextAnswer("https://www.bobsburgers.com"))
  )

  def firstQuestion(questionnaire: Questionnaire) = questionnaire.questions.head.question.id

}
