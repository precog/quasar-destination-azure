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

import scala.concurrent.duration.MILLISECONDS
import scala.Predef._
import scala._

import quasar.api.destination.{Destination, DestinationType, ResultSink}
import quasar.api.push.RenderConfig
import quasar.api.resource.ResourcePath
import quasar.blobstore.azure.{Azure, AzurePutService, Expires}
import quasar.blobstore.paths.{BlobPath, PathElem, Path}
import quasar.blobstore.services.PutService

import cats.effect.{ConcurrentEffect, ContextShift, Sync, Timer}
import cats.effect.concurrent.Ref
import cats.implicits._

import com.microsoft.azure.storage.blob.ContainerURL

import eu.timepit.refined.auto._

import fs2.Stream

import java.time.Instant

import org.slf4s.Logging

import scalaz.NonEmptyList

final case class AzureDestination[F[_]: ConcurrentEffect: ContextShift: Timer](
  refContainerURL: Ref[F, Expires[ContainerURL]],
  config: AzureConfig) extends Destination[F] with Logging {

  def destinationType: DestinationType = DestinationType("azure-dest", 1L)

  def sinks: NonEmptyList[ResultSink[F]] = NonEmptyList(csvSink)

  private def csvSink = ResultSink.csv[F](RenderConfig.Csv()) {
    case (path, _, bytes) => for {
      containerURL <- Stream.eval(refContainerURL.get)
      renewed <- Stream.eval(maybeRenew(containerURL))
      put: PutService[F] = AzurePutService.mk[F](renewed)
      _ <- Stream.eval(put((toBlobPath(path), bytes)).void)
    } yield ()
  }

  private def maybeRenew(url: Expires[ContainerURL]): F[ContainerURL] =
    for {
      epochNow <- Timer[F].clock.realTime(MILLISECONDS)
      now = Instant.ofEpochMilli(epochNow)
      expiresAt = url.expiresAt.toInstant
      _ <- debug(s"Credentials expire on: ${expiresAt}")
      containerURL0 <-
        if (now.isAfter(expiresAt))
          for {
            _ <- debug("Credentials expired, renewing...")
            newContainerURL <- Azure.mkContainerUrl[F](AzureConfig.toConfig(config))
            _ <- refContainerURL.set(newContainerURL)
            _ <- debug("Renewed credentials")
          } yield newContainerURL
        else
          debug("Credentials are still valid.") *> url.pure[F]
    } yield containerURL0.value

  private def debug(str: String): F[Unit] =
    Sync[F].delay(log.debug(str))

  private def toBlobPath(path: ResourcePath): BlobPath =
    BlobPath(toPath(path))

  private def toPath(path: ResourcePath): Path =
    ResourcePath.resourceNamesIso.get(path).map(n => PathElem(n.value)).toList
}
