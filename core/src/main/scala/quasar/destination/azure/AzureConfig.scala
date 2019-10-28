/*
 * Copyright 2014–2019 SlamData Inc.
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

package quasar.destination.azure

import scala.Some
import scala.Predef.String

import quasar.blobstore.azure.{
  AccountKey,
  AccountName,
  AzureCredentials,
  ContainerName,
  Config,
  DefaultConfig,
  MaxQueueSize,
  StorageUrl
}

import argonaut._, Argonaut._

final case class AzureConfig(
  containerName: ContainerName,
  storageUrl: StorageUrl,
  credentials: AzureCredentials)

object AzureConfig {
  implicit val azureCredentialsDecodeJson: DecodeJson[AzureCredentials] =
    jdecode2L[String, String, AzureCredentials]((accountName, accountKey) =>
      AzureCredentials(AccountName(accountName), AccountKey(accountKey)))(
      "accountName", "accountKey")

  implicit val azureConfigDecodeJson: DecodeJson[AzureConfig] =
    jdecode3L[String, String, AzureCredentials, AzureConfig]((cn, st, creds) =>
      AzureConfig(ContainerName(cn), StorageUrl(st), creds))(
      "container", "storageUrl", "credentials")

  implicit val azureCredentialsEncodeJson: EncodeJson[AzureCredentials] =
    EncodeJson(creds => Json.obj(
      "accountName" := creds.accountName.value,
      "accountKey" := creds.accountKey.value))

  implicit val azureConfigEncodeJson: EncodeJson[AzureConfig] =
    EncodeJson(cfg => Json.obj(
      "container" := cfg.containerName.value,
      "storageUrl" := cfg.storageUrl.value,
      "credentials" := cfg.credentials))

  def toConfig(azureConfig: AzureConfig): Config =
    DefaultConfig(
      azureConfig.containerName,
      Some(azureConfig.credentials),
      azureConfig.storageUrl,
      Some(MaxQueueSize.default))
}
