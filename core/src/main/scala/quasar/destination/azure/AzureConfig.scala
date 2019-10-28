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
import cats.syntax.option._

final case class AzureConfig(
  containerName: ContainerName,
  storageUrl: StorageUrl,
  credentials: AzureCredentials)

object AzureConfig {
  implicit val azureCredentialsDecodeJson: CodecJson[AzureCredentials] =
    casecodec2[String, String, AzureCredentials](
      (an, ak) => AzureCredentials(AccountName(an), AccountKey(ak)),
      creds => (creds.accountName.value, creds.accountKey.value).some)(
      "accountName", "accountKey")

  implicit val azureConfigDecodeJson: CodecJson[AzureConfig] =
    casecodec3[String, String, AzureCredentials, AzureConfig](
      (cn, st, creds) => AzureConfig(ContainerName(cn), StorageUrl(st), creds),
      cfg => (cfg.containerName.value, cfg.storageUrl.value, cfg.credentials).some)(
      "container", "storageUrl", "credentials")

  def toConfig(azureConfig: AzureConfig): Config =
    DefaultConfig(
      azureConfig.containerName,
      Some(azureConfig.credentials),
      azureConfig.storageUrl,
      Some(MaxQueueSize.default))
}
