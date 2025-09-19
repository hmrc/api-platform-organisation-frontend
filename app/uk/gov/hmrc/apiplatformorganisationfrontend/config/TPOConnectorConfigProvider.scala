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

package uk.gov.hmrc.apiplatformorganisationfrontend.config

import com.google.inject.{Inject, Provider, Singleton}

import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import uk.gov.hmrc.apiplatformorganisationfrontend.connectors.ThirdPartyOrchestratorConnector

@Singleton
class TPOConnectorConfigProvider @Inject() (config: ServicesConfig) extends Provider[ThirdPartyOrchestratorConnector.Config] {

  override def get(): ThirdPartyOrchestratorConnector.Config = ThirdPartyOrchestratorConnector.Config(
    serviceBaseUrl = config.baseUrl("third-party-orchestrator"))
}
