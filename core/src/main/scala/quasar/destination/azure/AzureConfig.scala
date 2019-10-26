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
    DecodeJson(c => for {
      accountName <- c.downField("accountName").as[String]
      accountKey <- c.downField("accountKey").as[String]
    } yield AzureCredentials(AccountName(accountName), AccountKey(accountKey)))

  implicit val azureConfigDecodeJson: DecodeJson[AzureConfig] =
    DecodeJson(c => for {
      containerName <- c.downField("container").as[String]
      storageUrl <- c.downField("storageUrl").as[String]
      credentials <- c.downField("credentials").as[AzureCredentials]
    } yield AzureConfig(
      ContainerName(containerName),
      StorageUrl(storageUrl),
      credentials))

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
