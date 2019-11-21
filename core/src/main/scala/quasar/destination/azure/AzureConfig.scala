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
  ClientId,
  ClientSecret,
  Config,
  ContainerName,
  DefaultConfig,
  MaxQueueSize,
  StorageUrl,
  TenantId
}

import argonaut._, Argonaut._
import cats.syntax.option._

final case class AzureConfig(
  containerName: ContainerName,
  storageUrl: StorageUrl,
  credentials: AzureCredentials)

object AzureConfig {
  implicit val credentialsCodec: CodecJson[AzureCredentials] =
    CodecJson({
      case AzureCredentials.SharedKey(AccountName(an), AccountKey(ak)) =>
        Json.obj(
          "auth" := "sharedKey",
          "accountName" := an,
          "accountKey" := ak)
      case AzureCredentials.ActiveDirectory(ClientId(cid), TenantId(tid), ClientSecret(cs)) =>
        Json.obj(
          "auth" := "activeDirectory",
          "clientId" := cid,
          "tenantId" := tid,
          "clientSecret" := cs)
    }, c => for {
      auth <- c.get[String]("auth")
      credentials <- auth match {
        case "sharedKey" => for {
          an <- c.get[String]("accountName")
          ak <- c.get[String]("accountKey")
        } yield AzureCredentials.SharedKey(AccountName(an), AccountKey(ak))

        case "activeDirectory" => for {
          cid <- c.get[String]("clientId")
          tid <- c.get[String]("tenantId")
          cs <- c.get[String]("clientSecret")
        } yield AzureCredentials.ActiveDirectory(ClientId(cid), TenantId(tid), ClientSecret(cs))

        case _ => DecodeResult.fail("auth must be 'sharedKey' or 'activeDirectory", c.history)
      }
    } yield credentials)

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
