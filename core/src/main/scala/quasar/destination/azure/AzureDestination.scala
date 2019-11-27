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

import scala._

import quasar.api.destination.{Destination, DestinationType, ResultSink}
import quasar.api.push.RenderConfig
import quasar.api.resource.ResourcePath
import quasar.blobstore.azure.{AzurePutService, Expires}
import quasar.blobstore.paths.{BlobPath, PathElem, Path}
import quasar.blobstore.services.PutService

import cats.effect.{ConcurrentEffect, ContextShift, Timer}
import cats.effect.concurrent.Ref
import cats.implicits._

import com.microsoft.azure.storage.blob.ContainerURL

import eu.timepit.refined.auto._

import fs2.Stream

import scalaz.NonEmptyList

final case class AzureDestination[F[_]: ConcurrentEffect: ContextShift: Timer](
  refContainerURL: Ref[F, Expires[ContainerURL]],
  refresh: F[Unit]) extends Destination[F] {

  def destinationType: DestinationType = DestinationType("azure-dest", 1L)

  def sinks: NonEmptyList[ResultSink[F]] = NonEmptyList(csvSink)

  private def csvSink = ResultSink.csv[F](RenderConfig.Csv()) {
    case (path, _, bytes) => Stream.eval(for {
      _ <- refresh
      containerURL <- refContainerURL.get
      put: PutService[F] = AzurePutService.mk[F](containerURL.value)
      _ <- put((toBlobPath(path), bytes))
    } yield ())
  }

  private def toBlobPath(path: ResourcePath): BlobPath =
    BlobPath(toPath(path))

  private def toPath(path: ResourcePath): Path =
    ResourcePath.resourceNamesIso.get(path).map(n => PathElem(n.value)).toList
}
